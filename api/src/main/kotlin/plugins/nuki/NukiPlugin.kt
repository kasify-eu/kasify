package eu.kasify.plugins.nuki

import eu.kasify.plugins.nuki.model.CreateVisitorCodeRequest
import eu.kasify.plugins.nuki.model.NukiAccessCode
import eu.kasify.plugins.nuki.model.NukiSmartLock
import eu.kasify.plugins.nuki.model.UpdateAccessCodeRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

const val PLUGIN_NAME = "NukiSmartLock"

open class NukiPlugin(
    private val apiToken: String,
    httpClient: HttpClient? = null,
) {
    private val logger = KotlinLogging.logger {}
    private val baseUrl = "https://api.nuki.io"
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
        }

    private val client =
        httpClient ?: HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
            }
            engine {
                dispatcher = Dispatchers.IO
            }
        }

    suspend fun unlock(smartLockId: Long) {
        try {
            logger.info { "Unlocking smart lock $smartLockId" }

            val response =
                client.post("$baseUrl/smartlock/$smartLockId/action") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"action": 1}""") // Action 1 = Unlock
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiException("Unauthorized access to Nuki API")
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to unlock smart lock: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to unlock smart lock $smartLockId: ${response.status.description}")
            }

            val responseText = response.bodyAsText()
            logger.debug { "Nuki API response: $responseText" }
            logger.info { "Smart lock $smartLockId unlocked successfully" }
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to unlock smart lock $smartLockId" }
            throw NukiException("Failed to unlock smart lock $smartLockId", e)
        }
    }

    suspend fun lock(smartLockId: Long) {
        try {
            logger.info { "Locking smart lock $smartLockId" }

            val response =
                client.post("$baseUrl/smartlock/$smartLockId/action") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"action": 2}""") // Action 2 = Lock
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiException("Unauthorized access to Nuki API")
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to lock smart lock: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to lock smart lock $smartLockId: ${response.status.description}")
            }

            val responseText = response.bodyAsText()
            logger.debug { "Nuki API response: $responseText" }
            logger.info { "Smart lock $smartLockId locked successfully" }
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to lock smart lock $smartLockId" }
            throw NukiException("Failed to lock smart lock $smartLockId", e)
        }
    }

    suspend fun openDoor(smartLockId: Long) {
        try {
            logger.info { "Opening door for smart lock $smartLockId" }

            val response =
                client.post("$baseUrl/smartlock/$smartLockId/action") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody("""{"action": 3}""") // Action 3 = Open door/unlatch
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiException("Unauthorized access to Nuki API")
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to open door: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to open door for smart lock $smartLockId: ${response.status.description}")
            }

            val responseText = response.bodyAsText()
            logger.debug { "Nuki API response: $responseText" }
            logger.info { "Door opened successfully for smart lock $smartLockId" }
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to open door for smart lock $smartLockId" }
            throw NukiException("Failed to open door for smart lock $smartLockId", e)
        }
    }

    open suspend fun getSmartLocks(): List<NukiSmartLock> {
        try {
            logger.info { "Fetching smart locks from Nuki API" }

            val response =
                client.get("$baseUrl/smartlock") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiUnauthorizedException()
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to fetch smart locks: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to fetch smart locks: ${response.status.description}")
            }

            val responseText = response.bodyAsText()
            logger.debug { "Nuki API response: $responseText" }

            return json.decodeFromString<List<NukiSmartLock>>(responseText)
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch smart locks from Nuki API" }
            throw NukiException("Error fetching smart locks from Nuki API", e)
        }
    }

    // 22599677111
    suspend fun getSmartLock(id: Long): NukiSmartLock {
        try {
            logger.info { "Fetching smart lock $id from Nuki API" }

            val response =
                client.get("$baseUrl/smartlock/$id") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiException("Unauthorized access to Nuki API")
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to fetch smart lock: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to fetch smart lock $id: ${response.status.description}")
            }

            val responseText = response.bodyAsText()
            logger.debug { "Nuki API response: ${response.status}" }
            logger.debug { "Nuki API response: $responseText" }

            return json.decodeFromString<NukiSmartLock>(responseText)
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch smart lock $id from Nuki API" }
            throw NukiException("Error fetching smart lock $id from Nuki API", e)
        }
    }

    suspend fun getAccessCodes(smartLockId: String): List<NukiAccessCode> {
        try {
            logger.info { "Fetching access codes for smart lock $smartLockId from Nuki API" }

            val response =
                client.get("$baseUrl/smartlock/$smartLockId/auth") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiException("Unauthorized access to Nuki API")
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to fetch access codes: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to fetch access codes for smart lock $smartLockId: ${response.status.description}")
            }

            // The auth list carries the keypad PIN of every authorization on the lock,
            // so never log the raw body: log only the count and the authorization ids.
            val responseText = response.bodyAsText()
            val accessCodes = json.decodeFromString<List<NukiAccessCode>>(responseText)
            logger.debug {
                "Nuki API response: ${accessCodes.size} authorizations for smart lock $smartLockId, ids: ${accessCodes.map { it.id }}"
            }

            return accessCodes
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch access codes for smart lock $smartLockId from Nuki API" }
            throw NukiException("Error fetching access codes for smart lock $smartLockId", e)
        }
    }

    // https://api.nuki.io/smartlock/22599677111/auth
    // https://api.nuki.io/smartlock/auth
    suspend fun createAccessCode(
        name: String,
        code: String,
        from: LocalDateTime,
        to: LocalDateTime,
        smartLockId: String,
    ) {
        try {
            logger.info { "Creating visitor code '$name' for smartLockId" }

            val request =
                CreateVisitorCodeRequest(
                    remoteAllowed = true,
                    name = name,
                    code = code,
                    allowedFromDate = from,
                    allowedUntilDate = to,
                )

            // single url call "$baseUrl/smartlock/22599677111/auth"
            val response =
                client.put("$baseUrl/smartlock/$smartLockId/auth") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(CreateVisitorCodeRequest.serializer(), request))
                }

            // if the call is successful this call return a 204 No Content

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiUnauthorizedException()
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to create visitor code. Status: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to create visitor code for '$name'. Status: ${response.status}, Response: $responseText")
            }

            logger.info { "Visitor code '$name' created successfully" }
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to create visitor code" }
            throw NukiException("Error creating visitor code for '$name'", e)
        }
    }

    suspend fun updateAccessCode(
        smartLockId: String,
        authId: String,
        from: LocalDateTime,
        to: LocalDateTime,
    ) {
        try {
            logger.info { "Updating access code $authId on smart lock $smartLockId" }

            val request =
                UpdateAccessCodeRequest(
                    allowedFromDate = from,
                    allowedUntilDate = to,
                )

            val response =
                client.post("$baseUrl/smartlock/$smartLockId/auth/$authId") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(UpdateAccessCodeRequest.serializer(), request))
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiUnauthorizedException()
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to update access code. Status: ${response.status}, Response: $responseText" }
                throw NukiException(
                    "Failed to update access code $authId on smart lock $smartLockId. Status: ${response.status}, Response: $responseText",
                )
            }

            logger.info { "Access code $authId updated successfully" }
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to update access code $authId on smart lock $smartLockId" }
            throw NukiException("Error updating access code $authId on smart lock $smartLockId", e)
        }
    }

    suspend fun deleteAccessCode(id: String) {
        try {
            logger.info { "Deleting access code with id: $id" }

            val codesToDelete = listOf(id)

            val response =
                client.delete("$baseUrl/smartlock/auth") {
                    header("Accept", "application/json")
                    header("Authorization", "Bearer $apiToken")
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(codesToDelete))
                }

            if (response.status == HttpStatusCode.Unauthorized) {
                logger.error { "Unauthorized access to Nuki API - token may be invalid" }
                throw NukiException("Unauthorized access to Nuki API")
            }

            if (!response.status.isSuccess()) {
                val responseText = response.bodyAsText()
                logger.error { "Failed to delete access code. Status: ${response.status}, Response: $responseText" }
                throw NukiException("Failed to delete access code with id: $id. Status: ${response.status}")
            }

            logger.info { "Access code deleted successfully" }
        } catch (e: NukiException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete access code with id: $id" }
            throw NukiException("Error deleting access code with id: $id", e)
        }
    }

    fun close() {
        client.close()
    }
}
