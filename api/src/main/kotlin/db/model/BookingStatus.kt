package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BookingStatusSerializer::class)
enum class BookingStatus {
    Confirmed,
    Cancelled,
}

object BookingStatusSerializer : KSerializer<BookingStatus> {
    override val descriptor = PrimitiveSerialDescriptor("BookingStatus", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BookingStatus {
        val value = decoder.decodeString()
        return BookingStatus.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid BookingStatus: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: BookingStatus,
    ) {
        encoder.encodeString(value.name)
    }
}
