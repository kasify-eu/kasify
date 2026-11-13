package eu.kasify.db.model

import eu.kasify.model.SesCommunicationOperation
import eu.kasify.model.SesCommunicationStatus
import eu.kasify.model.SesCommunicationType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object SesCommunicationsTable : Table(name = "ses_communication") {
    val id = uuid("id").autoGenerate()
    val bookingId = reference("booking_id", BookingsTable.id)

    val communicationType =
        customEnumeration(
            name = "communication_type",
            sql = "ses_communication_type",
            fromDb = { value -> SesCommunicationType.fromDb(value as String) },
            toDb = { PGEnum("ses_communication_type", it) },
        )

    val operation =
        customEnumeration(
            name = "operation",
            sql = "ses_communication_operation",
            fromDb = { value -> SesCommunicationOperation.fromDb(value as String) },
            toDb = { PGEnum("ses_communication_operation", it) },
        )

    val cancelsId = uuid("cancels_id").nullable()

    val status =
        customEnumeration(
            name = "status",
            sql = "ses_communication_status",
            fromDb = { value -> SesCommunicationStatus.fromDb(value as String) },
            toDb = { PGEnum("ses_communication_status", it) },
        )

    val lote = text("lote").nullable()
    val codigoComunicacion = text("codigo_comunicacion").nullable()

    val estado =
        customEnumeration(
            name = "estado",
            sql = "ses_codigo_estado",
            fromDb = { value -> SesCodigoEstado.fromDb(value as String) },
            toDb = { SesCodigoEstadoPGEnum(it) },
        ).nullable()

    val estadoDesc = text("estado_desc").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val lastAttemptAt = timestampWithTimeZone("last_attempt_at").nullable()
    val lastError = text("last_error").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
}
