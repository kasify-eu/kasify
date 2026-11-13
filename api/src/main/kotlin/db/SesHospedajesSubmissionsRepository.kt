package eu.kasify.db

import eu.kasify.db.model.SesCodigoEstado
import eu.kasify.db.model.SesHospedajesSubmissionsTable
import eu.kasify.model.SesHospedajesSubmission
import eu.kasify.model.SesSubmissionOperation
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class SesHospedajesSubmissionsRepository {
    suspend fun recordSubmitAttempt(
        bookingId: Uuid,
        respuestaCodigo: Int?,
        respuestaDescripcion: String?,
        sesLote: String?,
        responseXml: String?,
        errorText: String?,
        sesCommunicationId: Uuid? = null,
        requestXml: String? = null,
    ): Uuid =
        dbQuery {
            val attemptedAt = OffsetDateTime.now()
            val result =
                SesHospedajesSubmissionsTable.insert {
                    it[SesHospedajesSubmissionsTable.bookingId] = bookingId
                    it[SesHospedajesSubmissionsTable.operation] = SesSubmissionOperation.Submit.dbValue
                    it[SesHospedajesSubmissionsTable.attemptedAt] = attemptedAt
                    it[SesHospedajesSubmissionsTable.respuestaCodigo] = respuestaCodigo
                    it[SesHospedajesSubmissionsTable.respuestaDescripcion] = respuestaDescripcion
                    it[SesHospedajesSubmissionsTable.sesLote] = sesLote
                    it[SesHospedajesSubmissionsTable.responseXml] = responseXml
                    it[SesHospedajesSubmissionsTable.requestXml] = requestXml
                    it[SesHospedajesSubmissionsTable.errorText] = errorText?.take(1000)
                    it[SesHospedajesSubmissionsTable.sesCommunicationId] = sesCommunicationId
                }
            result[SesHospedajesSubmissionsTable.id]
        }

    suspend fun recordQueryAttempt(
        bookingId: Uuid,
        sesLote: String,
        respuestaCodigo: Int?,
        respuestaDescripcion: String?,
        sesEstado: SesCodigoEstado?,
        sesEstadoDesc: String?,
        responseXml: String?,
        errorText: String?,
        sesCommunicationId: Uuid? = null,
    ): Uuid =
        dbQuery {
            val attemptedAt = OffsetDateTime.now()
            val result =
                SesHospedajesSubmissionsTable.insert {
                    it[SesHospedajesSubmissionsTable.bookingId] = bookingId
                    it[SesHospedajesSubmissionsTable.operation] = SesSubmissionOperation.Query.dbValue
                    it[SesHospedajesSubmissionsTable.attemptedAt] = attemptedAt
                    it[SesHospedajesSubmissionsTable.respuestaCodigo] = respuestaCodigo
                    it[SesHospedajesSubmissionsTable.respuestaDescripcion] = respuestaDescripcion
                    it[SesHospedajesSubmissionsTable.sesLote] = sesLote
                    it[SesHospedajesSubmissionsTable.sesEstado] = sesEstado
                    it[SesHospedajesSubmissionsTable.sesEstadoDesc] = sesEstadoDesc
                    it[SesHospedajesSubmissionsTable.responseXml] = responseXml
                    it[SesHospedajesSubmissionsTable.errorText] = errorText?.take(1000)
                    it[SesHospedajesSubmissionsTable.sesCommunicationId] = sesCommunicationId
                }
            result[SesHospedajesSubmissionsTable.id]
        }

    suspend fun listSubmissionsForBooking(bookingId: Uuid): List<SesHospedajesSubmission> =
        dbQuery {
            SesHospedajesSubmissionsTable
                .selectAll()
                .where { SesHospedajesSubmissionsTable.bookingId eq bookingId }
                .orderBy(SesHospedajesSubmissionsTable.attemptedAt, SortOrder.DESC)
                .map { SesHospedajesSubmission.from(it) }
        }

    suspend fun latestSubmissionByBooking(bookingIds: Collection<Uuid>): Map<Uuid, SesHospedajesSubmission> {
        if (bookingIds.isEmpty()) return emptyMap()
        return dbQuery {
            SesHospedajesSubmissionsTable
                .selectAll()
                .where { SesHospedajesSubmissionsTable.bookingId inList bookingIds.toList() }
                .orderBy(SesHospedajesSubmissionsTable.attemptedAt, SortOrder.DESC)
                .map { SesHospedajesSubmission.from(it) }
                .groupBy { it.bookingId }
                // Already DESC-ordered → first is the latest per booking.
                .mapValues { (_, rows) -> rows.first() }
        }
    }

    suspend fun countByBooking(bookingIds: Collection<Uuid>): Map<Uuid, Int> {
        if (bookingIds.isEmpty()) return emptyMap()
        return dbQuery {
            SesHospedajesSubmissionsTable
                .selectAll()
                .where { SesHospedajesSubmissionsTable.bookingId inList bookingIds.toList() }
                .groupBy { it[SesHospedajesSubmissionsTable.bookingId] }
                .mapValues { (_, rows) -> rows.size }
        }
    }
}
