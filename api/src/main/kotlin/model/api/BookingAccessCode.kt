package eu.kasify.model.api
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
data class BookingAccessCode(
    val id: Uuid,
    @SerialName("booking_id")
    val bookingId: Uuid,
    @SerialName("smartlock_id")
    val smartlockId: Uuid,
    @SerialName("access_code")
    val accessCode: String,
    @SerialName("vendor_code_id")
    val vendorCodeId: String? = null,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("code_start_datetime")
    val codeStartDatetime: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("code_end_datetime")
    val codeEndDatetime: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("created_at")
    val createdAt: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("updated_at")
    val updatedAt: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("deleted_at")
    val deletedAt: LocalDateTime? = null,
    val smartlock: SmartLock? = null,
) {
    companion object {
        fun from(bookingAccessCode: eu.kasify.model.BookingAccessCode): BookingAccessCode =
            BookingAccessCode(
                id = bookingAccessCode.id,
                bookingId = bookingAccessCode.bookingId,
                smartlockId = bookingAccessCode.smartlockId,
                accessCode = bookingAccessCode.accessCode,
                vendorCodeId = bookingAccessCode.vendorCodeId,
                codeStartDatetime = bookingAccessCode.codeStartDatetime,
                codeEndDatetime = bookingAccessCode.codeEndDatetime,
                createdAt = bookingAccessCode.createdAt,
                updatedAt = bookingAccessCode.updatedAt,
                deletedAt = bookingAccessCode.deletedAt,
                smartlock = bookingAccessCode.smartlock?.let { SmartLock.from(it) },
            )
    }
}
