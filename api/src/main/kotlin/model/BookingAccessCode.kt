package eu.kasify.model
import eu.kasify.db.model.BookingAccessCodesTable
import org.jetbrains.exposed.v1.core.ResultRow
import java.time.LocalDateTime
import kotlin.uuid.Uuid

data class BookingAccessCode(
    val id: Uuid,
    val bookingId: Uuid,
    val smartlockId: Uuid,
    val accessCode: String,
    val vendorCodeId: String? = null,
    val codeStartDatetime: LocalDateTime,
    val codeEndDatetime: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
    val smartlock: PropertySmartlock? = null,
) {
    companion object {
        fun from(row: ResultRow): BookingAccessCode =
            BookingAccessCode(
                id = row[BookingAccessCodesTable.id],
                bookingId = row[BookingAccessCodesTable.bookingId],
                smartlockId = row[BookingAccessCodesTable.smartlockId],
                accessCode = row[BookingAccessCodesTable.accessCode],
                vendorCodeId = row[BookingAccessCodesTable.vendorCodeId],
                codeStartDatetime = row[BookingAccessCodesTable.codeStartDatetime].toLocalDateTime(),
                codeEndDatetime = row[BookingAccessCodesTable.codeEndDatetime].toLocalDateTime(),
                createdAt = row[BookingAccessCodesTable.createdAt].toLocalDateTime(),
                updatedAt = row[BookingAccessCodesTable.updatedAt].toLocalDateTime(),
                deletedAt = row[BookingAccessCodesTable.deletedAt]?.toLocalDateTime(),
                smartlock = null,
            )

        fun fromWithSmartlock(row: ResultRow): BookingAccessCode =
            BookingAccessCode(
                id = row[BookingAccessCodesTable.id],
                bookingId = row[BookingAccessCodesTable.bookingId],
                smartlockId = row[BookingAccessCodesTable.smartlockId],
                accessCode = row[BookingAccessCodesTable.accessCode],
                vendorCodeId = row[BookingAccessCodesTable.vendorCodeId],
                codeStartDatetime = row[BookingAccessCodesTable.codeStartDatetime].toLocalDateTime(),
                codeEndDatetime = row[BookingAccessCodesTable.codeEndDatetime].toLocalDateTime(),
                createdAt = row[BookingAccessCodesTable.createdAt].toLocalDateTime(),
                updatedAt = row[BookingAccessCodesTable.updatedAt].toLocalDateTime(),
                deletedAt = row[BookingAccessCodesTable.deletedAt]?.toLocalDateTime(),
                smartlock = PropertySmartlock.from(row),
            )
    }
}
