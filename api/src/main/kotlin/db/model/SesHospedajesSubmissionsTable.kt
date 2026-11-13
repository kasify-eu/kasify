package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object SesHospedajesSubmissionsTable : Table(name = "ses_hospedajes_submissions") {
    val id = uuid("id").autoGenerate()
    val bookingId = reference("booking_id", BookingsTable.id)
    val operation = text("operation")
    val attemptedAt = timestampWithTimeZone("attempted_at")
    val respuestaCodigo = integer("respuesta_codigo").nullable()
    val respuestaDescripcion = text("respuesta_descripcion").nullable()
    val sesLote = text("ses_lote").nullable()
    val sesEstado =
        customEnumeration(
            name = "ses_estado",
            sql = "ses_codigo_estado",
            fromDb = { value -> SesCodigoEstado.fromDb(value as String) },
            toDb = { SesCodigoEstadoPGEnum(it) },
        ).nullable()
    val sesEstadoDesc = text("ses_estado_desc").nullable()
    val errorText = text("error_text").nullable()
    val responseXml = text("response_xml").nullable()
    val requestXml = text("request_xml").nullable()
    val sesCommunicationId = uuid("ses_communication_id").nullable()
}
