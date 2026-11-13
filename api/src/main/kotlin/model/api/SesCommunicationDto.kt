package eu.kasify.model.api

import eu.kasify.serialization.JavaOffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

/**
 * A single SES communication (one `(booking, type, operation)`) as exposed on the booking API.
 * The UI derives a per-type status from the alta/anulación rows (see `communicationStatus` in the
 * dashboard). Gated to MANAGER visibility wherever it is embedded (e.g. `Booking.sesCommunications`).
 */
@Serializable
class SesCommunicationDto(
    val id: Uuid,
    val type: String,
    val operation: String,
    val status: String,
    val lote: String? = null,
    @SerialName("codigo_comunicacion")
    val codigoComunicacion: String? = null,
    val estado: String? = null,
    @SerialName("estado_desc")
    val estadoDesc: String? = null,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("submitted_at")
    val submittedAt: OffsetDateTime? = null,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("last_attempt_at")
    val lastAttemptAt: OffsetDateTime? = null,
    @SerialName("last_error")
    val lastError: String? = null,
) {
    companion object {
        fun from(model: eu.kasify.model.SesCommunication): SesCommunicationDto =
            SesCommunicationDto(
                id = model.id,
                type = model.communicationType.dbValue,
                operation = model.operation.dbValue,
                status = model.status.dbValue,
                lote = model.lote,
                codigoComunicacion = model.codigoComunicacion,
                estado = model.estado?.dbValue,
                estadoDesc = model.estadoDesc,
                submittedAt = model.submittedAt,
                lastAttemptAt = model.lastAttemptAt,
                lastError = model.lastError,
            )
    }
}
