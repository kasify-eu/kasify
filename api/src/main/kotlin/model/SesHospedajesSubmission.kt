package eu.kasify.model
import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.db.model.SesHospedajesSubmissionsTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

enum class SesSubmissionOperation(
    val dbValue: String,
) {
    Submit("submit"),
    Query("query"),
    ;

    companion object {
        fun fromDb(value: String): SesSubmissionOperation =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown SES submission operation: $value")
    }
}

data class SesHospedajesSubmission(
    val id: Uuid,
    val bookingId: Uuid,
    val operation: SesSubmissionOperation,
    val attemptedAt: OffsetDateTime,
    val respuestaCodigo: Int?,
    val respuestaDescripcion: String?,
    val sesLote: String?,
    val sesEstado: SesCodigoEstado?,
    val sesEstadoDesc: String?,
    val errorText: String?,
    val responseXml: String?,
    val requestXml: String?,
    val sesCommunicationId: Uuid?,
) {
    companion object {
        fun from(row: ResultRow): SesHospedajesSubmission =
            SesHospedajesSubmission(
                id = row[SesHospedajesSubmissionsTable.id],
                bookingId = row[SesHospedajesSubmissionsTable.bookingId],
                operation = SesSubmissionOperation.fromDb(row[SesHospedajesSubmissionsTable.operation]),
                attemptedAt = row[SesHospedajesSubmissionsTable.attemptedAt],
                respuestaCodigo = row[SesHospedajesSubmissionsTable.respuestaCodigo],
                respuestaDescripcion = row[SesHospedajesSubmissionsTable.respuestaDescripcion],
                sesLote = row[SesHospedajesSubmissionsTable.sesLote],
                sesEstado = row[SesHospedajesSubmissionsTable.sesEstado],
                sesEstadoDesc = row[SesHospedajesSubmissionsTable.sesEstadoDesc],
                errorText = row[SesHospedajesSubmissionsTable.errorText],
                responseXml = row[SesHospedajesSubmissionsTable.responseXml],
                requestXml = row[SesHospedajesSubmissionsTable.requestXml],
                sesCommunicationId = row[SesHospedajesSubmissionsTable.sesCommunicationId],
            )
    }
}
