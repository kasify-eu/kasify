package eu.kasify.plugins.ses.internal

import eu.kasify.plugins.ses.SesEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class SesSoapClientTest {
    private fun clientCapturingUrl(
        endpointOverride: String?,
        capturedUrl: (String) -> Unit,
    ): SesSoapClient {
        val mockEngine =
            MockEngine { request ->
                capturedUrl(request.url.toString())
                respond(content = ByteReadChannel("<ok/>"), status = HttpStatusCode.OK)
            }
        return SesSoapClient(
            username = "user",
            password = "pass",
            environment = SesEnvironment.TEST,
            httpClient = HttpClient(mockEngine),
            endpointOverride = endpointOverride,
        )
    }

    @Test
    fun `posts to the environment baseUrl when no override is set`() =
        runTest {
            var url: String? = null
            val client = clientCapturingUrl(endpointOverride = null) { url = it }

            client.post("<envelope/>")

            assertEquals(SesEnvironment.TEST.baseUrl, url)
        }

    @Test
    fun `blank override is ignored`() =
        runTest {
            var url: String? = null
            val client = clientCapturingUrl(endpointOverride = "  ") { url = it }

            client.post("<envelope/>")

            assertEquals(SesEnvironment.TEST.baseUrl, url)
        }

    @Test
    fun `SES_ENDPOINT_URL override wins over the environment baseUrl`() =
        runTest {
            var url: String? = null
            val override = "http://localhost:8888/hospedajes-web/ws/v1/comunicacion"
            val client = clientCapturingUrl(endpointOverride = override) { url = it }

            client.post("<envelope/>")

            assertEquals(override, url)
        }
}
