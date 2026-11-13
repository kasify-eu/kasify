package eu.kasify.model.api

import eu.kasify.serialization.JavaOffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

@Serializable
data class ApiToken(
    val id: Uuid,
    val name: String,
    @SerialName("token_prefix")
    val tokenPrefix: String,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("last_used_at")
    val lastUsedAt: OffsetDateTime?,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("expires_at")
    val expiresAt: OffsetDateTime?,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("created_at")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(token: eu.kasify.model.ApiToken): ApiToken =
            ApiToken(
                id = token.id,
                name = token.name,
                tokenPrefix = token.tokenPrefix,
                lastUsedAt = token.lastUsedAt,
                expiresAt = token.expiresAt,
                createdAt = token.createdAt,
            )
    }
}
