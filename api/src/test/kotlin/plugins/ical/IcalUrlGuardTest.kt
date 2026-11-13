package eu.kasify.plugins.ical

import eu.kasify.model.api.requests.NewPropertyRequest
import eu.kasify.model.api.requests.validate
import io.ktor.http.Url
import io.ktor.server.plugins.requestvalidation.ValidationResult
import org.junit.Test
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcalUrlGuardTest {
    private fun ipv4(vararg octets: Int): ByteArray = ByteArray(4) { octets[it].toByte() }

    @Test
    fun `should accept public http and https feed URLs`() {
        listOf(
            "https://www.airbnb.com/calendar/ical/123.ics?s=secret",
            "https://admin.booking.com/hotel/hoteladmin/ical.html?t=abc",
            "http://www.airbnb.es/calendar/ical/123.ics",
            "HTTPS://WWW.AIRBNB.COM/calendar/ical/123.ics",
            "http://8.8.8.8/feed.ics",
            "http://[2001:4860:4860::8888]/feed.ics",
        ).forEach { url -> assertTrue(IcalUrlGuard.isAllowedUrl(url), url) }
    }

    @Test
    fun `should accept public IPs written in non-canonical numeric forms`() {
        // All are 8.8.8.8 under inet_aton rules; java.net.URL accepted them before the guard existed.
        listOf(
            "http://134744072/feed.ics",
            "http://8.8.2056/",
            "http://0x08080808/",
            "http://010.010.010.010/",
            "http://0x8.0x8.0x8.0x8/",
            "http://8.526344/",
        ).forEach { url -> assertTrue(IcalUrlGuard.isAllowedUrl(url), url) }
    }

    @Test
    fun `should reject internal targets at store time`() {
        listOf(
            "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
            "http://localhost:8080/",
            "http://LOCALHOST./",
            "http://api.localhost/",
            "http://127.0.0.1/",
            "http://10.0.0.5:9000/",
            "http://172.16.3.4/",
            "http://192.168.1.1/",
            "http://100.100.100.200/",
            "http://0.0.0.0/",
            "http://0/",
            "http://2130706433/",
            "http://0177.0.0.1/",
            "http://0x7f.0.0.1/",
            "http://127.1/",
            "http://127.0.0.1./",
            "http://[::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://[fd00:ec2::254]/",
            "http://[fe80::1]/",
        ).forEach { url -> assertFalse(IcalUrlGuard.isAllowedUrl(url), url) }
    }

    @Test
    fun `should reject non-http schemes, missing hosts and malformed URLs`() {
        listOf(
            "ftp://www.airbnb.com/feed.ics",
            "file:///etc/passwd",
            "jar:file:/tmp/x.jar!/feed.ics",
            "http:/feed.ics",
            "not a url",
        ).forEach { url -> assertFalse(IcalUrlGuard.isAllowedUrl(url), url) }
    }

    @Test
    fun `should decode numeric hosts with inet_aton rules`() {
        val localhost = ipv4(127, 0, 0, 1)
        listOf("2130706433", "0177.0.0.1", "0x7f.0.0.1", "127.1", "0x7F000001", "017700000001", "127.0.1")
            .forEach { host -> assertContentEquals(localhost, IcalUrlGuard.decodeInetAtonIpv4(host), host) }

        val googleDns = ipv4(8, 8, 8, 8)
        listOf("134744072", "8.8.2056", "0x08080808", "8.8.8.8")
            .forEach { host -> assertContentEquals(googleDns, IcalUrlGuard.decodeInetAtonIpv4(host), host) }
        // inet_aton reads a bare "0x" part as 0.
        assertContentEquals(ipv4(0, 0, 0, 0), IcalUrlGuard.decodeInetAtonIpv4("0x"))
    }

    @Test
    fun `should not decode hostnames or out-of-range numbers`() {
        listOf(
            "www.airbnb.com",
            "cafe",
            "1.2.3.4.5",
            "256.1.1.1",
            "1.2.3.256",
            "4294967296",
            "08.1.1.1",
            "0xg.1.1.1",
            "1..1",
            "",
            "+1.2.3.4",
            "99999999999999999999999",
        ).forEach { host -> assertNull(IcalUrlGuard.decodeInetAtonIpv4(host), host) }
    }

    @Test
    fun `should classify public and non-public addresses`() {
        listOf(
            "8.8.8.8",
            "1.1.1.1",
            "93.184.216.34",
            "2606:4700:4700::1111",
            "2a03:2880:f12f:83:face:b00c:0:25de",
            "64:ff9b::808:808", // NAT64 of 8.8.8.8
            "2002:808:808::1", // 6to4 of 8.8.8.8
        ).forEach { literal -> assertTrue(IcalUrlGuard.isPublicAddress(InetAddress.getByName(literal)), literal) }

        listOf(
            "0.0.0.0",
            "10.1.2.3",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.169.254",
            "172.31.255.255",
            "192.0.2.1",
            "192.168.0.1",
            "198.18.0.1",
            "224.0.0.1",
            "255.255.255.255",
            "::",
            "::1",
            "::127.0.0.1",
            "64:ff9b::a9fe:a9fe", // NAT64 of 169.254.169.254
            "64:ff9b:1::1",
            "2002:7f00:1::1", // 6to4 of 127.0.0.1
            "2001::1", // Teredo
            "2001:db8::1",
            "fc00::1",
            "fd00:ec2::254", // AWS IMDS over IPv6
            "fe80::1",
            "fec0::1",
            "ff02::1",
        ).forEach { literal -> assertFalse(IcalUrlGuard.isPublicAddress(InetAddress.getByName(literal)), literal) }
    }

    @Test
    fun `should judge an IPv6-form IPv4-mapped address by its embedded IPv4`() {
        val mappedLoopback =
            ByteArray(16).also {
                it[10] = 0xFF.toByte()
                it[11] = 0xFF.toByte()
                it[12] = 127
                it[15] = 1
            }
        val address = Inet6Address.getByAddress(null, mappedLoopback, -1)
        assertIs<Inet6Address>(address)
        assertFalse(IcalUrlGuard.isPublicAddress(address))
    }

    @Test
    fun `should return the first validated address for a public target`() {
        val first = InetAddress.getByName("93.184.216.34")
        val second = InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946")

        val address = IcalUrlGuard.requirePublicTarget(Url("https://example.com/feed.ics")) { listOf(first, second) }

        assertEquals(first, address)
    }

    private fun assertRefused(
        url: String,
        resolveHost: (String) -> List<InetAddress>,
    ) {
        val exception = assertFailsWith<CalendarFeedException>(url) { IcalUrlGuard.requirePublicTarget(Url(url), resolveHost) }
        assertEquals(IcalUrlGuard.URL_NOT_ALLOWED_MESSAGE, exception.message, url)
    }

    @Test
    fun `should refuse a fetch target for every reason with one message`() {
        val public = InetAddress.getByName("93.184.216.34")
        assertRefused("wss://example.com/feed") { listOf(public) }
        assertRefused("http://127.0.0.1/") { listOf(public) }
        assertRefused("http://example.com/") { emptyList() }
        assertRefused("http://example.com/") { throw UnknownHostException(it) }
        assertRefused("http://example.com/") { listOf(InetAddress.getByName("10.0.0.1")) }
    }

    @Test
    fun `should format pinned hosts as URL literals`() {
        assertEquals("93.184.216.34", IcalUrlGuard.urlHost(InetAddress.getByName("93.184.216.34")))
        assertEquals("[2606:4700:4700:0:0:0:0:1111]", IcalUrlGuard.urlHost(InetAddress.getByName("2606:4700:4700::1111")))
    }

    private fun propertyRequest(
        airbnbIcalUrl: String? = null,
        bookingIcalUrl: String? = null,
    ) = NewPropertyRequest(
        name = "Casa",
        address = "Rue 1",
        city = "Paris",
        zipcode = "75001",
        countryCode = "FRA",
        airbnbIcalUrl = airbnbIcalUrl,
        bookingIcalUrl = bookingIcalUrl,
    )

    @Test
    fun `property validation should reject internal iCal URLs and accept public ones`() {
        assertEquals(
            ValidationResult.Valid,
            propertyRequest(
                airbnbIcalUrl = "https://www.airbnb.com/calendar/ical/123.ics?s=secret",
                bookingIcalUrl = "http://134744072/feed.ics",
            ).validate(),
        )

        val invalid =
            propertyRequest(
                airbnbIcalUrl = "http://169.254.169.254/latest/meta-data/",
                bookingIcalUrl = "http://0x7f.0.0.1:8080/",
            ).validate()
        assertIs<ValidationResult.Invalid>(invalid)
        assertEquals(
            listOf("Airbnb iCal URL must be a valid URL", "Booking.com iCal URL must be a valid URL"),
            invalid.reasons,
        )
    }
}
