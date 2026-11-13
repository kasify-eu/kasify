package eu.kasify.plugins.unifi
import eu.kasify.plugins.unifi.model.UnifiAccessGroupResource
import eu.kasify.plugins.unifi.model.UnifiApiResponseV2
import eu.kasify.plugins.unifi.model.UnifiCreateVisitorSimplifiedRequest
import eu.kasify.plugins.unifi.model.UnifiGroupInfoRequest
import eu.kasify.plugins.unifi.model.UnifiGroupResourceItemRequest
import eu.kasify.plugins.unifi.model.UnifiInlineHolidayTimezone
import eu.kasify.plugins.unifi.model.UnifiInlineSchedule
import eu.kasify.plugins.unifi.model.UnifiInlineTimeSlot
import eu.kasify.plugins.unifi.model.UnifiInlineUserTimezone
import eu.kasify.plugins.unifi.model.UnifiLoginRequest
import eu.kasify.plugins.unifi.model.UnifiLoginResponse
import eu.kasify.plugins.unifi.model.UnifiResourceItem
import eu.kasify.plugins.unifi.model.UnifiResourceItemRequest
import eu.kasify.plugins.unifi.model.UnifiResourceRequest
import eu.kasify.plugins.unifi.model.UnifiUpdateVisitorRequest
import eu.kasify.plugins.unifi.model.UnifiVisitorV2
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Unifi Access Plugin V2 - Handles communication with Unifi Access API
 *
 * This plugin manages visitor access codes (PIN codes) for Unifi Access smart locks.
 * It uses cookie-based authentication with CSRF token protection.
 *
 * Usage:
 * ```
 * val plugin = UnifiAccessPluginV2("https://unifi-console.example.com")
 * try {
 *     plugin.login(username, password)
 *     val visitor = plugin.createVisitorSimplified(...)
 * } finally {
 *     plugin.close()
 * }
 * ```
 */
class UnifiAccessPluginV2(
    private val baseUrl: String,
    httpClient: HttpClient? = null,
) {
    private val logger = KotlinLogging.logger {}
    private var csrfToken: String? = null
    private var isLoggedIn: Boolean = false

    private companion object {
        /** Longest console- or request-supplied value (e.g. a response body) written to a log line. */
        const val MAX_LOGGED_VALUE_LENGTH = 2000

        /** Bound on how many causes [sanitizedCopy] follows, as a guard against cyclic chains. */
        const val MAX_SANITIZED_CAUSE_DEPTH = 10
    }

    /**
     * Makes a console- or request-supplied value safe to embed in a log line or an exception
     * message (CWE-117): CR, LF, every other control character and the Unicode line/paragraph
     * separators are escaped so the value cannot start a forged log entry, and overlong values
     * are truncated. Ordinary printable text is returned unchanged.
     */
    private fun sanitizeForLog(value: Any?): String {
        val text = value.toString()
        val truncated = text.length > MAX_LOGGED_VALUE_LENGTH
        return buildString {
            for (c in if (truncated) text.take(MAX_LOGGED_VALUE_LENGTH) else text) {
                when {
                    c == '\r' -> append("\\r")
                    c == '\n' -> append("\\n")
                    c == '\t' -> append("\\t")
                    Character.isISOControl(c) || c == '\u2028' || c == '\u2029' ->
                        append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else -> append(c)
                }
            }
            if (truncated) append("...[truncated ${text.length - MAX_LOGGED_VALUE_LENGTH} chars]")
        }
    }

    /**
     * Returns a log-safe stand-in for [error] and its cause chain: each link keeps the original
     * class name and stack trace, but its message goes through [sanitizeForLog]. Exceptions raised
     * while talking to the console can embed console-supplied text in their message
     * (kotlinx.serialization echoes the raw JSON input, for instance), so the raw exception must
     * be neither logged nor chained, or every caller that logs the chain would write it verbatim.
     */
    private fun sanitizedCopy(
        error: Throwable,
        depth: Int = 0,
    ): Throwable {
        val cause =
            error.cause
                ?.takeIf { it !== error && depth < MAX_SANITIZED_CAUSE_DEPTH }
                ?.let { sanitizedCopy(it, depth + 1) }
        val message =
            error.message?.let { "${error.javaClass.name}: ${sanitizeForLog(it)}" } ?: error.javaClass.name
        return UnifiSanitizedException(message, cause).also { it.stackTrace = error.stackTrace }
    }

    // Store raw cookie headers from login response for session persistence
    private var rawCookieHeaders: List<String> = emptyList()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }

    private val client =
        httpClient ?: HttpClient(CIO) {
            install(HttpCookies)

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                retryOnException(maxRetries = 2, retryOnTimeout = true)
                exponentialDelay()
            }

            engine {
                maxConnectionsCount = 10
                endpoint {
                    connectTimeout = 10_000
                    keepAliveTime = 5_000
                }
            }
        }

    /**
     * Builds the API endpoint URL for Unifi Access proxy
     */
    private fun buildEndpoint(path: String): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$baseUrl/proxy/access$normalizedPath"
    }

    /**
     * Generates a unique request ID for tracing
     */
    private fun generateRequestId(): String = UUID.randomUUID().toString().take(8)

    /**
     * Login to Unifi OS device to establish a cookie-based session.
     *
     * @param username The username for authentication
     * @param password The password for authentication
     * @return UnifiLoginResponse with user details
     * @throws UnifiAuthenticationException if login fails
     */
    suspend fun login(
        username: String,
        password: String,
    ): UnifiLoginResponse {
        val requestId = generateRequestId()
        logger.info { "[$requestId] Logging in to Unifi OS at $baseUrl (user: ${sanitizeForLog(username)})" }

        try {
            val loginRequest =
                UnifiLoginRequest(
                    username = username,
                    password = password,
                    remember = true,
                )

            val startTime = System.currentTimeMillis()
            val response =
                client.post("$baseUrl/api/auth/login") {
                    header("Accept", "application/json")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(UnifiLoginRequest.serializer(), loginRequest))
                }
            val duration = System.currentTimeMillis() - startTime

            val responseText = response.bodyAsText()

            // Extract CSRF token (don't log the actual value)
            csrfToken = response.headers["X-CSRF-Token"] ?: response.headers["x-csrf-token"]
            val hasCsrfToken = csrfToken != null
            val hasCookies = !response.headers.getAll(HttpHeaders.SetCookie).isNullOrEmpty()

            logger.debug {
                "[$requestId] Login response received in ${duration}ms - " +
                    "status=${sanitizeForLog(response.status)}, hasCsrfToken=$hasCsrfToken, hasCookies=$hasCookies"
            }

            if (!response.status.isSuccess()) {
                logger.error {
                    "[$requestId] Login failed - status=${sanitizeForLog(response.status)}, " +
                        "duration=${duration}ms, response=${sanitizeForLog(responseText)}"
                }
                throw UnifiAuthenticationException(
                    "Login failed with status ${sanitizeForLog(response.status)}",
                    requestId,
                )
            }

            if (!hasCsrfToken) {
                logger.warn { "[$requestId] No CSRF token received - subsequent requests may fail" }
            }

            // Capture raw cookie headers for session persistence
            rawCookieHeaders = response.headers.getAll(HttpHeaders.SetCookie) ?: emptyList()

            val loginResponse = json.decodeFromString<UnifiLoginResponse>(responseText)
            isLoggedIn = true

            logger.info {
                "[$requestId] Successfully logged in as ${sanitizeForLog(loginResponse.username)} in ${duration}ms"
            }

            return loginResponse
        } catch (e: UnifiAuthenticationException) {
            throw e
        } catch (e: Exception) {
            val safeError = sanitizedCopy(e)
            logger.error(safeError) { "[$requestId] Login failed due to unexpected error" }
            throw UnifiApiException("Login failed: ${sanitizeForLog(e.message)}", requestId, safeError)
        }
    }

    /**
     * Updates a visitor's start and end times in Unifi Access.
     *
     * @param uniqueId The unique ID of the visitor to update
     * @param startTimeUTC New start time of access (UTC)
     * @param endTimeUTC New end time of access (UTC)
     * @throws UnifiApiException if update fails
     */
    suspend fun updateVisitor(
        uniqueId: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
    ) {
        val requestId = generateRequestId()
        // The visitor id was issued by the console when the visitor was created.
        val safeVisitorId = sanitizeForLog(uniqueId)
        logger.info {
            "[$requestId] Updating visitor times (visitorId=$safeVisitorId, startTime=$startTimeUTC, endTime=$endTimeUTC)"
        }

        ensureLoggedIn(requestId)

        try {
            val request =
                UnifiUpdateVisitorRequest(
                    startTime = startTimeUTC.toEpochSecond(ZoneOffset.UTC),
                    endTime = endTimeUTC.toEpochSecond(ZoneOffset.UTC),
                )

            val endpoint = buildEndpoint("/api/v2/visitor/$uniqueId")
            val requestBody = json.encodeToString(UnifiUpdateVisitorRequest.serializer(), request)

            val startTime = System.currentTimeMillis()
            val response =
                client.put(endpoint) {
                    contentType(ContentType.Application.Json)
                    header("Accept", "application/json, text/plain, */*")
                    header("Origin", baseUrl)
                    header("Referer", "$baseUrl/")
                    csrfToken?.let { header("X-CSRF-Token", it) }
                    buildCookieHeader()?.let { header(HttpHeaders.Cookie, it) }
                    setBody(requestBody)
                }
            val duration = System.currentTimeMillis() - startTime

            val responseText = response.bodyAsText()

            if (!response.status.isSuccess()) {
                logger.error {
                    "[$requestId] Failed to update visitor - visitorId=$safeVisitorId, " +
                        "status=${sanitizeForLog(response.status)}, duration=${duration}ms, " +
                        "response=${sanitizeForLog(responseText)}"
                }
                throw UnifiApiException(
                    "Failed to update visitor $safeVisitorId: ${sanitizeForLog(response.status)}",
                    requestId,
                )
            }

            logger.info { "[$requestId] Visitor updated successfully (visitorId=$safeVisitorId) in ${duration}ms" }
        } catch (e: UnifiApiException) {
            throw e
        } catch (e: Exception) {
            val safeError = sanitizedCopy(e)
            logger.error(safeError) { "[$requestId] Unexpected error updating visitor (visitorId=$safeVisitorId)" }
            throw UnifiApiException(
                "Failed to update visitor $safeVisitorId: ${sanitizeForLog(e.message)}",
                requestId,
                safeError,
            )
        }
    }

    /**
     * Deletes a visitor from Unifi Access.
     *
     * @param uniqueId The unique ID of the visitor to delete
     * @throws UnifiApiException if deletion fails
     */
    suspend fun deleteVisitor(uniqueId: String) {
        val requestId = generateRequestId()
        // The visitor id was issued by the console when the visitor was created.
        val safeVisitorId = sanitizeForLog(uniqueId)
        logger.info { "[$requestId] Deleting visitor (visitorId=$safeVisitorId)" }

        ensureLoggedIn(requestId)

        try {
            val endpoint = buildEndpoint("/api/v2/visitor/$uniqueId?is_force=true")

            val startTime = System.currentTimeMillis()
            val response =
                client.delete(endpoint) {
                    header("Accept", "application/json, text/plain, */*")
                    header("Origin", baseUrl)
                    header("Referer", "$baseUrl/")
                    csrfToken?.let { header("X-CSRF-Token", it) }
                    buildCookieHeader()?.let { header(HttpHeaders.Cookie, it) }
                }
            val duration = System.currentTimeMillis() - startTime

            val responseText = response.bodyAsText()

            if (!response.status.isSuccess()) {
                logger.error {
                    "[$requestId] Failed to delete visitor - visitorId=$safeVisitorId, " +
                        "status=${sanitizeForLog(response.status)}, duration=${duration}ms, " +
                        "response=${sanitizeForLog(responseText)}"
                }
                throw UnifiApiException(
                    "Failed to delete visitor $safeVisitorId: ${sanitizeForLog(response.status)}",
                    requestId,
                )
            }

            logger.info { "[$requestId] Visitor deleted successfully (visitorId=$safeVisitorId) in ${duration}ms" }
        } catch (e: UnifiApiException) {
            throw e
        } catch (e: Exception) {
            val safeError = sanitizedCopy(e)
            logger.error(safeError) { "[$requestId] Unexpected error deleting visitor (visitorId=$safeVisitorId)" }
            throw UnifiApiException(
                "Failed to delete visitor $safeVisitorId: ${sanitizeForLog(e.message)}",
                requestId,
                safeError,
            )
        }
    }

    /**
     * Creates a visitor with inline 24/7 schedule.
     * This is the recommended approach for guest access.
     *
     * @param firstName First name of the visitor
     * @param lastName Last name of the visitor
     * @param startTimeUTC Start time of access (UTC)
     * @param endTimeUTC End time of access (UTC)
     * @param floorId The floor ID containing the door
     * @param doorId The door ID to grant access to
     * @param pinCode PIN code for access
     * @param remarks Optional remarks
     * @return The created visitor
     * @throws UnifiApiException if creation fails
     */
    suspend fun createVisitorSimplified(
        firstName: String,
        lastName: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
        floorId: String,
        doorId: String,
        pinCode: String,
        remarks: String = "",
    ): UnifiVisitorV2 {
        val requestId = generateRequestId()
        val visitorName = "$firstName $lastName"
        logger.info {
            "[$requestId] Creating visitor (name=$visitorName, floorId=$floorId, doorId=$doorId, " +
                "startTime=$startTimeUTC, endTime=$endTimeUTC)"
        }

        ensureLoggedIn(requestId)

        try {
            val timestamp = System.currentTimeMillis()
            val startTimeSeconds = startTimeUTC.toEpochSecond(ZoneOffset.UTC)
            val endTimeSeconds = endTimeUTC.toEpochSecond(ZoneOffset.UTC)

            // Build 24/7 week schedule (all days, all times)
            val allDaySlot = listOf(UnifiInlineTimeSlot("00:00:00", "23:59:59"))
            val weekSchedule = (0..6).associate { it.toString() to allDaySlot }

            val schedule =
                UnifiInlineSchedule(
                    name = "visitor-$timestamp",
                    scheduleType = "access",
                    isPrivate = true,
                    holidayTimezone = UnifiInlineHolidayTimezone(name = "holiday $timestamp"),
                    userTimezone =
                        UnifiInlineUserTimezone(
                            name = "user $timestamp",
                            weekSchedule = weekSchedule,
                        ),
                )

            // Build resource with access_group structure
            val resource =
                listOf(
                    UnifiResourceRequest(
                        groupInfo =
                            UnifiGroupInfoRequest(
                                groupName = "visitor_$timestamp",
                                groupResources =
                                    listOf(
                                        UnifiGroupResourceItemRequest(
                                            resourceId = floorId,
                                            resourceType = "floor",
                                            resources =
                                                listOf(
                                                    UnifiResourceItemRequest(
                                                        resourceId = doorId,
                                                        resourceType = "door",
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                    ),
                )

            val request =
                UnifiCreateVisitorSimplifiedRequest(
                    firstName = firstName,
                    lastName = lastName,
                    remarks = remarks,
                    startTime = startTimeSeconds,
                    endTime = endTimeSeconds,
                    pinCode = pinCode,
                    resource = resource,
                    schedule = schedule,
                )

            val endpoint = buildEndpoint("/api/v2/visitor")
            val requestBody =
                json.encodeToString(
                    UnifiCreateVisitorSimplifiedRequest.serializer(),
                    request,
                )

            logger.debug { "[$requestId] Request body: ${redactPinCodeForLog(requestBody)}" }

            val startTime2 = System.currentTimeMillis()
            val response =
                client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header("Accept", "application/json, text/plain, */*")
                    header("Origin", baseUrl)
                    header("Referer", "$baseUrl/")
                    csrfToken?.let { header("X-CSRF-Token", it) }
                    buildCookieHeader()?.let { header(HttpHeaders.Cookie, it) }
                    setBody(requestBody)
                }
            val duration = System.currentTimeMillis() - startTime2

            val responseText = response.bodyAsText()
            logger.debug { "[$requestId] Response (${duration}ms): ${redactPinCodeForLog(responseText)}" }

            if (!response.status.isSuccess()) {
                logger.error {
                    "[$requestId] Failed to create visitor - name=$visitorName, " +
                        "status=${response.status}, duration=${duration}ms, " +
                        "response=${redactPinCodeForLog(responseText)}"
                }
                throw UnifiApiException(
                    "Failed to create visitor: HTTP ${response.status}",
                    requestId,
                )
            }

            val apiResponse = json.decodeFromString<UnifiApiResponseV2<UnifiVisitorV2>>(responseText)

            if (apiResponse.codeS != "SUCCESS" || apiResponse.data == null) {
                logger.error {
                    "[$requestId] API error creating visitor - name=$visitorName, " +
                        "code=${apiResponse.codeS}, msg=${apiResponse.msg}"
                }
                throw UnifiApiException(
                    "Failed to create visitor: ${apiResponse.msg}",
                    requestId,
                )
            }

            val visitorId = apiResponse.data.uniqueId
            logger.info {
                "[$requestId] Visitor created successfully (visitorId=$visitorId, name=$visitorName) in ${duration}ms"
            }

            return apiResponse.data
        } catch (e: UnifiApiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "[$requestId] Unexpected error creating visitor (name=$visitorName)" }
            throw UnifiApiException("Failed to create visitor: ${e.message}", requestId, e)
        }
    }

    /**
     * Retrieves all doors from Unifi Access.
     *
     * @return List of door resources with their floor IDs
     * @throws UnifiApiException if fetching fails
     */
    suspend fun getDoors(): List<UnifiResourceItem> {
        val requestId = generateRequestId()
        logger.info { "[$requestId] Fetching doors from Unifi Access" }

        ensureLoggedIn(requestId)

        try {
            val queryParams =
                buildString {
                    append("?show_door=true")
                    append("&show_elevator_v2=false")
                    append("&show_access_group=false")
                    append("&show_building=false")
                }

            val endpoint = buildEndpoint("/api/v2/access_groups/resources$queryParams")

            val startTime = System.currentTimeMillis()
            val response =
                client.get(endpoint) {
                    header("Accept", "application/json")
                    csrfToken?.let { header("X-CSRF-Token", it) }
                    buildCookieHeader()?.let { header(HttpHeaders.Cookie, it) }
                }
            val duration = System.currentTimeMillis() - startTime

            val responseText = response.bodyAsText()
            logger.debug { "[$requestId] Response (${duration}ms): ${sanitizeForLog(responseText)}" }

            if (!response.status.isSuccess()) {
                logger.error {
                    "[$requestId] Failed to fetch doors - status=${sanitizeForLog(response.status)}, " +
                        "duration=${duration}ms, response=${sanitizeForLog(responseText)}"
                }
                throw UnifiApiException(
                    "Failed to fetch doors: HTTP ${sanitizeForLog(response.status)}",
                    requestId,
                )
            }

            val apiResponse = json.decodeFromString<UnifiApiResponseV2<List<UnifiAccessGroupResource>>>(responseText)

            if (apiResponse.codeS != "SUCCESS" || apiResponse.data == null) {
                logger.error {
                    "[$requestId] API error fetching doors - code=${sanitizeForLog(apiResponse.codeS)}, " +
                        "msg=${sanitizeForLog(apiResponse.msg)}"
                }
                throw UnifiApiException("Failed to fetch doors: ${sanitizeForLog(apiResponse.msg)}", requestId)
            }

            // Extract all doors from the nested structure, including the floor ID from parent
            val doors =
                apiResponse.data
                    .flatMap { it.groupResources }
                    .filter { it.resourceType == "floor" }
                    .flatMap { floor ->
                        floor.resources
                            .filter { it.resourceType == "door" }
                            .map { door -> door.copy(floorId = floor.resourceId) }
                    }

            logger.info { "[$requestId] Fetched ${doors.size} doors in ${duration}ms" }
            return doors
        } catch (e: UnifiApiException) {
            throw e
        } catch (e: Exception) {
            val safeError = sanitizedCopy(e)
            logger.error(safeError) { "[$requestId] Unexpected error fetching doors" }
            throw UnifiApiException("Failed to fetch doors: ${sanitizeForLog(e.message)}", requestId, safeError)
        }
    }

    /**
     * Ensures the client is logged in before making API calls.
     */
    private fun ensureLoggedIn(requestId: String) {
        if (!isLoggedIn) {
            logger.warn { "[$requestId] Attempting API call without login" }
        }
    }

    /**
     * Extracts session data (CSRF token + cookies) for persistence.
     * Call this after login() to save the session for later restoration.
     *
     * @return UnifiSessionData containing the CSRF token and serialized cookies
     */
    fun extractSessionData(): UnifiSessionData {
        val cookiesJson =
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(SerializableCookie.serializer()),
                rawCookieHeaders.map { cookieHeader ->
                    // Parse the Set-Cookie header to extract name and value
                    val parts = cookieHeader.split(";").first().split("=", limit = 2)
                    SerializableCookie(
                        name = parts.getOrElse(0) { "" },
                        value = parts.getOrElse(1) { "" },
                    )
                },
            )
        return UnifiSessionData(
            csrfToken = csrfToken ?: "",
            cookiesJson = cookiesJson,
        )
    }

    /**
     * Restores a previously saved session (CSRF token + cookies).
     * After calling this method, the plugin is ready to make authenticated API calls
     * without needing to call login().
     *
     * @param csrfToken The CSRF token from the saved session
     * @param cookiesJson The JSON-serialized cookies from the saved session
     */
    fun restoreSession(
        csrfToken: String,
        cookiesJson: String,
    ) {
        logger.debug { "Restoring session from saved data (baseUrl=$baseUrl)" }

        this.csrfToken = csrfToken

        // Deserialize cookies and store them for manual injection
        val cookies =
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(SerializableCookie.serializer()),
                cookiesJson,
            )

        // Store cookies for manual injection in requests
        this.restoredCookies = cookies

        this.isLoggedIn = true
        logger.info { "Session restored successfully (baseUrl=$baseUrl)" }
    }

    // Cookies restored from saved session, used for manual injection
    private var restoredCookies: List<SerializableCookie> = emptyList()

    /**
     * Builds a cookie header string from restored cookies.
     */
    private fun buildCookieHeader(): String? {
        if (restoredCookies.isEmpty()) return null
        return restoredCookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * Closes the HTTP client and releases resources.
     * Should be called when the plugin is no longer needed.
     */
    fun close() {
        logger.debug { "Closing Unifi Access plugin (baseUrl=$baseUrl)" }
        client.close()
        isLoggedIn = false
        csrfToken = null
    }
}

/**
 * Data class for persisting Unifi session data.
 */
data class UnifiSessionData(
    val csrfToken: String,
    val cookiesJson: String,
)

/**
 * Serializable cookie for JSON persistence.
 */
@Serializable
data class SerializableCookie(
    val name: String,
    val value: String,
)

/**
 * Exception thrown when Unifi API authentication fails.
 */
class UnifiAuthenticationException(
    message: String,
    val requestId: String,
    cause: Throwable? = null,
) : Exception("[$requestId] $message", cause)

/**
 * Exception thrown when a Unifi API call fails.
 */
class UnifiApiException(
    message: String,
    val requestId: String,
    cause: Throwable? = null,
) : Exception("[$requestId] $message", cause)

private const val PIN_CODE_FIELD = "pin_code"
private const val PIN_CODE_REDACTED = "[REDACTED]"
private const val PIN_CODE_WITHHELD = "<withheld: body contains pin_code>"

// Nesting bound checked before parsing: both the JSON parser (for arrays) and the redaction walk
// recurse per level, so a pathologically deep body could otherwise exhaust the stack.
private const val PIN_CODE_REDACTION_MAX_DEPTH = 32

/**
 * Makes a visitor request/response body safe to log by replacing every `pin_code` member with
 * [PIN_CODE_REDACTED]. The request carries the guest's door PIN there and the response its
 * `pin_code_token`. Bodies that do not mention `pin_code` are returned unchanged; bodies that do
 * but nest deeper than [PIN_CODE_REDACTION_MAX_DEPTH] or cannot be parsed are withheld entirely.
 */
private fun redactPinCodeForLog(body: String): String {
    if (!body.contains(PIN_CODE_FIELD)) return body
    if (jsonNestingExceeds(body, PIN_CODE_REDACTION_MAX_DEPTH)) return PIN_CODE_WITHHELD
    val element =
        try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            return PIN_CODE_WITHHELD
        }
    return redactPinCode(element).toString()
}

/** Iteratively scans [body] and reports whether its `{`/`[` nesting (outside strings) exceeds [maxDepth]. */
private fun jsonNestingExceeds(
    body: String,
    maxDepth: Int,
): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in body) {
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{', '[' -> if (++depth > maxDepth) return true
                '}', ']' -> if (depth > 0) depth--
            }
        }
    }
    return false
}

/** Returns [element] with every `pin_code` member replaced; depth is bounded by [jsonNestingExceeds]. */
private fun redactPinCode(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject ->
            JsonObject(
                element.mapValues { (key, value) ->
                    if (key == PIN_CODE_FIELD) JsonPrimitive(PIN_CODE_REDACTED) else redactPinCode(value)
                },
            )
        is JsonArray -> JsonArray(element.map { redactPinCode(it) })
        else -> element
    }

/**
 * Log-safe stand-in for an exception raised while talking to a Unifi console, built by
 * `UnifiAccessPluginV2.sanitizedCopy`. Its message is "<original class>: <escaped message>" and it
 * carries the original stack trace.
 */
private class UnifiSanitizedException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)
