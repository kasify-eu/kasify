package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object BookingAccessCodesTable : Table(name = "booking_access_codes") {
    val id = uuid("id").autoGenerate()
    val bookingId = reference("booking_id", BookingsTable.id)
    val smartlockId = reference("smartlock_id", PropertySmartlocksTable.id)

    val accessCode = text("access_code")
    val vendorCodeId = text("vendor_code_id").nullable()

    val codeStartDatetime = timestampWithTimeZone("code_start_datetime")
    val codeEndDatetime = timestampWithTimeZone("code_end_datetime")

    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
