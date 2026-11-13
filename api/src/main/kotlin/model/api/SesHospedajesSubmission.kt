package eu.kasify.model.api

import eu.kasify.serialization.JavaOffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

@Serializable
class SesHospedajesSubmission(
    val id: Uuid,
    @SerialName("booking_id")
    val bookingId: Uuid,
    val operation: String,
    @Serializable(with = JavaOffsetDateTimeSerializer::class)
    @SerialName("attempted_at")
    val attemptedAt: OffsetDateTime,
    @SerialName("respuesta_codigo")
    val respuestaCodigo: Int? = null,
    @SerialName("respuesta_descripcion")
    val respuestaDescripcion: String? = null,
    @SerialName("ses_lote")
    val sesLote: String? = null,
    @SerialName("ses_estado")
    val sesEstado: String? = null,
    @SerialName("ses_estado_desc")
    val sesEstadoDesc: String? = null,
    @SerialName("error_text")
    val errorText: String? = null,
    @SerialName("ses_communication_id")
    val sesCommunicationId: Uuid? = null,
    @VisibleTo(Visibility.OWNER)
    @SerialName("response_xml")
    val responseXml: String? = null,
) {
    companion object {
        fun from(
            model: eu.kasify.model.SesHospedajesSubmission,
            includeResponseXml: Boolean = false,
        ): SesHospedajesSubmission =
            SesHospedajesSubmission(
                id = model.id,
                bookingId = model.bookingId,
                operation = model.operation.dbValue,
                attemptedAt = model.attemptedAt,
                respuestaCodigo = model.respuestaCodigo,
                respuestaDescripcion = model.respuestaDescripcion,
                sesLote = model.sesLote,
                sesEstado = model.sesEstado?.dbValue,
                sesEstadoDesc = model.sesEstadoDesc,
                errorText = model.errorText,
                sesCommunicationId = model.sesCommunicationId,
                responseXml = if (includeResponseXml) model.responseXml else null,
            )
    }
}
