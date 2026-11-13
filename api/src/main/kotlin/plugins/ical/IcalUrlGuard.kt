package eu.kasify.plugins.ical

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import java.net.Inet6Address
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URL

private val logger = KotlinLogging.logger {}

/**
 * SSRF guard for the user-supplied iCal feed URLs (`airbnb_ical_url` / `booking_ical_url`).
 *
 * The feed is fetched server-side and parts of the response end up in the user-visible
 * `last_error`, so a feed URL must never reach loopback, private, link-local, CGNAT, multicast or
 * other non-public addresses (cloud metadata endpoints, internal services). Two layers:
 *
 * - [isAllowedUrl] — store-time, DNS-free: http(s) only, a host is required, and `localhost` names
 *   and IP literals (including the inet_aton numeric forms such as `2130706433` or `0x7f.0.0.1`)
 *   must be public. Ordinary hostnames pass; they can only be judged once resolved.
 * - [requirePublicTarget] — fetch-time, before every request hop (first request, retry and each
 *   redirect): resolves the host and requires every address to be public.
 *
 * Every refusal surfaces the same [URL_NOT_ALLOWED_MESSAGE], whatever the reason (non-public
 * address, unresolvable host, scheme), so `last_error` can't be used to map the internal network.
 */
object IcalUrlGuard {
    const val URL_NOT_ALLOWED_MESSAGE = "calendar.feed.url_not_allowed"

    private val ALLOWED_SCHEMES = setOf("http", "https")

    /** Default resolver: the JVM's, i.e. the same one the CIO engine connects with. */
    fun resolveSystem(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()

    /** Store-time check for a feed URL. Performs no DNS lookups. */
    fun isAllowedUrl(url: String): Boolean {
        val parsed =
            try {
                URL(url)
            } catch (_: MalformedURLException) {
                return false
            }
        return parsed.protocol.lowercase() in ALLOWED_SCHEMES && isAllowedHost(parsed.host)
    }

    /**
     * Fetch-time check for one request hop. Returns the validated address the request must be sent
     * to, or throws [CalendarFeedException] with [URL_NOT_ALLOWED_MESSAGE].
     */
    fun requirePublicTarget(
        target: Url,
        resolveHost: (String) -> List<InetAddress>,
    ): InetAddress {
        if (target.protocol.name !in ALLOWED_SCHEMES) throw notAllowed("scheme")
        if (!isAllowedHost(target.host)) throw notAllowed("host_literal")
        val addresses =
            try {
                resolveHost(target.host)
            } catch (_: Exception) {
                throw notAllowed("unresolvable")
            }
        if (addresses.isEmpty() || !addresses.all(::isPublicAddress)) throw notAllowed("non_public_address")
        return addresses.first()
    }

    /** The URL host form of [address] (IPv6 in brackets), with any scope id dropped. */
    fun urlHost(address: InetAddress): String {
        val literal = InetAddress.getByAddress(address.address)
        return if (literal is Inet6Address) "[${literal.hostAddress}]" else literal.hostAddress
    }

    private fun notAllowed(reason: String): CalendarFeedException {
        // The reason is for operators only; the user-visible message is identical for every case.
        logger.warn { "calendar.feed.url_not_allowed reason=$reason" }
        return CalendarFeedException(URL_NOT_ALLOWED_MESSAGE)
    }

    /**
     * DNS-free host check: rejects blank hosts, `localhost` names and non-public IP literals.
     * Any other hostname is allowed here and judged by its resolved addresses at fetch time.
     */
    internal fun isAllowedHost(host: String): Boolean {
        val normalized = host.lowercase().removeSuffix(".")
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return false
        if (normalized.startsWith("[")) {
            // Bracketed IPv6 literal: the JDK parses it numerically and never falls back to DNS.
            val address =
                try {
                    InetAddress.getByName(normalized)
                } catch (_: Exception) {
                    return false
                }
            return isPublicAddress(address)
        }
        val ipv4 = decodeInetAtonIpv4(normalized) ?: return true
        return isPublicAddress(InetAddress.getByAddress(ipv4))
    }

    /**
     * Decodes [host] as an IPv4 address using inet_aton rules, which resolvers apply to numeric
     * hosts: 1 to 4 dot-separated parts, each decimal, `0`-prefixed octal or `0x`-prefixed hex,
     * with the last part filling the remaining bytes (`127.1`, `2130706433`, `0x7f.0.0.1` are all
     * 127.0.0.1). Returns null when [host] is not such a number.
     */
    internal fun decodeInetAtonIpv4(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size > IPV4_BYTES) return null
        val values = parts.map { parseInetAtonPart(it) ?: return null }
        if (values.dropLast(1).any { it > BYTE_MAX }) return null
        val lastPartBits = Byte.SIZE_BITS * (IPV4_BYTES - values.size + 1)
        if (values.last() >= (1L shl lastPartBits)) return null

        var value = values.last()
        values.dropLast(1).forEachIndexed { index, part ->
            value = value or (part shl (Byte.SIZE_BITS * (IPV4_BYTES - 1 - index)))
        }
        return ByteArray(IPV4_BYTES) { index ->
            (value shr (Byte.SIZE_BITS * (IPV4_BYTES - 1 - index))).toByte()
        }
    }

    private fun parseInetAtonPart(part: String): Long? {
        val (digits, radix) =
            when {
                part.startsWith("0x") || part.startsWith("0X") -> part.substring(2) to HEX_RADIX
                part.length > 1 && part.startsWith("0") -> part.substring(1) to OCTAL_RADIX
                else -> part to DECIMAL_RADIX
            }
        // inet_aton reads a bare "0x" as 0; any other part needs at least one digit.
        if (digits.isEmpty()) return if (radix == HEX_RADIX) 0L else null
        if (!digits.all { it.isAsciiDigitIn(radix) }) return null
        val value = digits.trimStart('0').ifEmpty { "0" }
        if (value.length > MAX_PART_DIGITS) return null
        return value.toLong(radix).takeIf { it <= UINT32_MAX }
    }

    private fun Char.isAsciiDigitIn(radix: Int): Boolean {
        val digit =
            when (this) {
                in '0'..'9' -> this - '0'
                in 'a'..'f' -> this - 'a' + DECIMAL_RADIX
                in 'A'..'F' -> this - 'A' + DECIMAL_RADIX
                else -> return false
            }
        return digit < radix
    }

    /**
     * True when [address] is publicly routable unicast. Everything else — loopback, private,
     * link-local, CGNAT, multicast, reserved and documentation ranges, IPv6 ULA / site-local — is
     * refused. IPv4 embedded in IPv6 (mapped, NAT64, 6to4) is judged by the embedded address.
     */
    internal fun isPublicAddress(address: InetAddress): Boolean {
        val bytes = address.address
        return when (bytes.size) {
            IPV4_BYTES -> isPublicIpv4(bytes)
            IPV6_BYTES -> isPublicIpv6(bytes)
            else -> false
        }
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean = BLOCKED_IPV4.none { it.contains(bytes) }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        embeddedIpv4(bytes)?.let { return isPublicIpv4(it) }
        // Only 2000::/3 is allocated as global unicast; carve out its non-public pieces.
        return GLOBAL_UNICAST_IPV6.contains(bytes) && BLOCKED_IPV6.none { it.contains(bytes) }
    }

    /** The IPv4 address carried by an IPv4-mapped, NAT64 (64:ff9b::/96) or 6to4 (2002::/16) address. */
    private fun embeddedIpv4(bytes: ByteArray): ByteArray? =
        when {
            isIpv4Mapped(bytes) || NAT64.contains(bytes) -> bytes.copyOfRange(12, 16)
            SIX_TO_FOUR.contains(bytes) -> bytes.copyOfRange(2, 6)
            else -> null
        }

    /** ::ffff:0:0/96. Kept as a byte check: the JDK folds a mapped literal into an IPv4 address. */
    private fun isIpv4Mapped(bytes: ByteArray): Boolean = (0 until 10).all { bytes[it] == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()

    private class Cidr(
        network: String,
        private val prefixLength: Int,
    ) {
        // Literal addresses only, so this never performs a DNS lookup.
        private val prefix: ByteArray = InetAddress.getByName(network).address

        fun contains(bytes: ByteArray): Boolean {
            if (bytes.size != prefix.size) return false
            val fullBytes = prefixLength / Byte.SIZE_BITS
            for (index in 0 until fullBytes) {
                if (bytes[index] != prefix[index]) return false
            }
            val remainingBits = prefixLength % Byte.SIZE_BITS
            if (remainingBits == 0) return true
            val mask = (BYTE_MAX shl (Byte.SIZE_BITS - remainingBits)) and BYTE_MAX
            return (bytes[fullBytes].toInt() and mask) == (prefix[fullBytes].toInt() and mask)
        }
    }

    private const val IPV4_BYTES = 4
    private const val IPV6_BYTES = 16
    private const val BYTE_MAX = 0xFF
    private const val UINT32_MAX = 0xFFFF_FFFFL
    private const val MAX_PART_DIGITS = 11 // past every uint32 in any radix, still fits a Long
    private const val HEX_RADIX = 16
    private const val OCTAL_RADIX = 8
    private const val DECIMAL_RADIX = 10

    private val BLOCKED_IPV4 =
        listOf(
            Cidr("0.0.0.0", 8), // "this network"; 0.0.0.0 reaches the local host
            Cidr("10.0.0.0", 8), // private
            Cidr("100.64.0.0", 10), // CGNAT (also hosts some cloud metadata services)
            Cidr("127.0.0.0", 8), // loopback
            Cidr("169.254.0.0", 16), // link-local, cloud metadata (169.254.169.254)
            Cidr("172.16.0.0", 12), // private
            Cidr("192.0.0.0", 24), // IETF protocol assignments
            Cidr("192.0.2.0", 24), // TEST-NET-1
            Cidr("192.88.99.0", 24), // deprecated 6to4 relay anycast
            Cidr("192.168.0.0", 16), // private
            Cidr("198.18.0.0", 15), // benchmarking
            Cidr("198.51.100.0", 24), // TEST-NET-2
            Cidr("203.0.113.0", 24), // TEST-NET-3
            Cidr("224.0.0.0", 4), // multicast
            Cidr("240.0.0.0", 4), // reserved, broadcast
        )

    private val NAT64 = Cidr("64:ff9b::", 96)
    private val SIX_TO_FOUR = Cidr("2002::", 16)
    private val GLOBAL_UNICAST_IPV6 = Cidr("2000::", 3)

    private val BLOCKED_IPV6 =
        listOf(
            Cidr("2001::", 32), // Teredo
            Cidr("2001:db8::", 32), // documentation
            Cidr("3fff::", 20), // documentation
        )
}
