package eu.kasify.model

import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.db.model.SesCommunicationsTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

enum class SesCommunicationType(
    val dbValue: String,
) {
    PV("pv"),
    RH("rh"),
    ;

    companion object {
        fun fromDb(value: String): SesCommunicationType =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown SES communication type: $value")
    }
}

enum class SesCommunicationOperation(
    val dbValue: String,
) {
    Alta("alta"),
    Anulacion("anulacion"),
    ;

    companion object {
        fun fromDb(value: String): SesCommunicationOperation =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown SES communication operation: $value")
    }
}

enum class SesCommunicationStatus(
    val dbValue: String,
) {
    Submitted("submitted"),
    Processing("processing"),
    Accepted("accepted"),
    Rejected("rejected"),
    Failed("failed"),
    ;

    companion object {
        fun fromDb(value: String): SesCommunicationStatus =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown SES communication status: $value")
    }
}

data class SesCommunication(
    val id: Uuid,
    val bookingId: Uuid,
    val communicationType: SesCommunicationType,
    val operation: SesCommunicationOperation,
    val cancelsId: Uuid?,
    val status: SesCommunicationStatus,
    val lote: String?,
    val codigoComunicacion: String?,
    val estado: SesCodigoEstado?,
    val estadoDesc: String?,
    val submittedAt: OffsetDateTime?,
    val lastAttemptAt: OffsetDateTime?,
    val lastError: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(row: ResultRow): SesCommunication =
            SesCommunication(
                id = row[SesCommunicationsTable.id],
                bookingId = row[SesCommunicationsTable.bookingId],
                communicationType = row[SesCommunicationsTable.communicationType],
                operation = row[SesCommunicationsTable.operation],
                cancelsId = row[SesCommunicationsTable.cancelsId],
                status = row[SesCommunicationsTable.status],
                lote = row[SesCommunicationsTable.lote],
                codigoComunicacion = row[SesCommunicationsTable.codigoComunicacion],
                estado = row[SesCommunicationsTable.estado],
                estadoDesc = row[SesCommunicationsTable.estadoDesc],
                submittedAt = row[SesCommunicationsTable.submittedAt],
                lastAttemptAt = row[SesCommunicationsTable.lastAttemptAt],
                lastError = row[SesCommunicationsTable.lastError],
                createdAt = row[SesCommunicationsTable.createdAt],
                updatedAt = row[SesCommunicationsTable.updatedAt],
            )
    }
}
