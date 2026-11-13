package eu.kasify.plugins.resend

import com.resend.Resend
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.Tag
import eu.kasify.model.email.EmailMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResendClientImpl(
    apiKey: String,
) : ResendClient {
    private val logger = KotlinLogging.logger {}
    private val resend = Resend(apiKey)

    override suspend fun send(message: EmailMessage): String =
        withContext(Dispatchers.IO) {
            val builder =
                CreateEmailOptions
                    .builder()
                    .from(message.from)
                    .to(message.to)
                    .subject(message.subject)
                    .html(message.html)
                    .text(message.text)

            if (message.tags.isNotEmpty()) {
                val tags =
                    message.tags.map { (name, value) ->
                        Tag
                            .builder()
                            .name(name)
                            .value(value)
                            .build()
                    }
                builder.tags(tags)
            }

            try {
                val response = resend.emails().send(builder.build())
                logger.info { "Sent Resend email id=${response.id} to=${message.to} tags=${message.tags}" }
                response.id
            } catch (e: Exception) {
                throw ResendException("Resend API call failed: ${e.message}", e)
            }
        }
}
