package eu.kasify.db
import eu.kasify.db.model.BookingAccessCodesTable
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyNukiSmartlocksTable
import eu.kasify.db.model.PropertySmartlocksTable
import eu.kasify.db.model.PropertyUnifiSmartlocksTable
import eu.kasify.model.BookingAccessCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.uuid.Uuid

class BookingAccessCodesRepository {
    private val madridZoneId = ZoneId.of("Europe/Madrid")

    /**
     * Get all access codes for a booking
     */
    suspend fun getByBookingId(bookingId: Uuid): List<BookingAccessCode> =
        dbQuery {
            BookingAccessCodesTable
                .innerJoin(PropertySmartlocksTable, { smartlockId }, { PropertySmartlocksTable.id })
                .leftJoin(PropertyNukiSmartlocksTable, { PropertySmartlocksTable.id }, { PropertyNukiSmartlocksTable.propertySmartlockId })
                .leftJoin(
                    PropertyUnifiSmartlocksTable,
                    { PropertySmartlocksTable.id },
                    { PropertyUnifiSmartlocksTable.propertySmartlockId },
                ).selectAll()
                .where {
                    (BookingAccessCodesTable.bookingId eq bookingId) and
                        BookingAccessCodesTable.deletedAt.isNull()
                }.map { BookingAccessCode.fromWithSmartlock(it) }
        }

    /**
     * Get a specific access code by ID
     */
    suspend fun getById(id: Uuid): BookingAccessCode? =
        dbQuery {
            BookingAccessCodesTable
                .selectAll()
                .where {
                    (BookingAccessCodesTable.id eq id) and
                        BookingAccessCodesTable.deletedAt.isNull()
                }.firstNotNullOfOrNull { BookingAccessCode.from(it) }
        }

    /**
     * Create a new access code for a booking on a specific smartlock
     */
    suspend fun create(
        bookingId: Uuid,
        smartlockId: Uuid,
        accessCode: String,
        codeStartDatetime: LocalDateTime,
        codeEndDatetime: LocalDateTime,
        vendorCodeId: String? = null,
    ): BookingAccessCode? =
        dbQuery {
            val now = Instant.now().atZone(madridZoneId).toOffsetDateTime()
            val id =
                BookingAccessCodesTable.insert {
                    it[BookingAccessCodesTable.bookingId] = bookingId
                    it[BookingAccessCodesTable.smartlockId] = smartlockId
                    it[BookingAccessCodesTable.accessCode] = accessCode
                    it[BookingAccessCodesTable.vendorCodeId] = vendorCodeId
                    it[BookingAccessCodesTable.codeStartDatetime] = codeStartDatetime.atZone(madridZoneId).toOffsetDateTime()
                    it[BookingAccessCodesTable.codeEndDatetime] = codeEndDatetime.atZone(madridZoneId).toOffsetDateTime()
                    it[BookingAccessCodesTable.createdAt] = now
                    it[BookingAccessCodesTable.updatedAt] = now
                }[BookingAccessCodesTable.id]

            BookingAccessCodesTable
                .selectAll()
                .where { BookingAccessCodesTable.id eq id }
                .firstNotNullOfOrNull { BookingAccessCode.from(it) }
        }

    /**
     * Update the validity window of an existing access code.
     */
    suspend fun updateDates(
        id: Uuid,
        codeStartDatetime: LocalDateTime,
        codeEndDatetime: LocalDateTime,
    ) {
        dbQuery {
            val now = Instant.now().atZone(madridZoneId).toOffsetDateTime()
            BookingAccessCodesTable.update({ BookingAccessCodesTable.id eq id }) {
                it[BookingAccessCodesTable.codeStartDatetime] = codeStartDatetime.atZone(madridZoneId).toOffsetDateTime()
                it[BookingAccessCodesTable.codeEndDatetime] = codeEndDatetime.atZone(madridZoneId).toOffsetDateTime()
                it[BookingAccessCodesTable.updatedAt] = now
            }
        }
    }

    /**
     * Delete an access code from the database (hard delete)
     */
    suspend fun delete(id: Uuid) {
        dbQuery {
            BookingAccessCodesTable.deleteWhere { BookingAccessCodesTable.id eq id }
        }
    }

    /**
     * Check if an access code is already used for any non-deleted booking in a property
     */
    suspend fun isAccessCodeUsed(
        propertyId: Uuid,
        accessCode: String,
    ): Boolean =
        dbQuery {
            BookingAccessCodesTable
                .innerJoin(BookingsTable, { BookingAccessCodesTable.bookingId }, { BookingsTable.id })
                .innerJoin(PropertiesTable, { BookingsTable.propertyId }, { PropertiesTable.id })
                .selectAll()
                .where {
                    (PropertiesTable.id eq propertyId) and
                        (BookingAccessCodesTable.accessCode eq accessCode) and
                        (BookingAccessCodesTable.deletedAt.isNull()) and
                        (PropertiesTable.deletedAt.isNull())
                }.count() > 0
        }

    /**
     * Generate a unique 6-digit access code for a property
     */
    suspend fun generateUniqueAccessCode(propertyId: Uuid): String {
        val allowedDigits = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9')
        var attempts = 0
        val maxAttempts = 100

        while (attempts < maxAttempts) {
            // Generate 6-digit code without 0
            val code =
                buildString {
                    repeat(6) {
                        append(allowedDigits.random())
                    }
                }

            // Check if code starts with "12"
            if (code.startsWith("12")) {
                attempts++
                continue
            }

            // Check if code is unique for this property
            if (!isAccessCodeUsed(propertyId, code)) {
                return code
            }

            attempts++
        }

        throw IllegalStateException("Failed to generate unique access code after $maxAttempts attempts")
    }
}
