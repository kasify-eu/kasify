package eu.kasify.model.api

import eu.kasify.serialization.JavaOffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

@Serializable
data class CreateApiTokenResponse(
    val id: Uuid,
    val name: String,
    val token: String,
    @SerialName("token_prefix")
    val tokenPrefix: String,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("expires_at")
    val expiresAt: OffsetDateTime?,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("created_at")
    val createdAt: OffsetDateTime,
)
