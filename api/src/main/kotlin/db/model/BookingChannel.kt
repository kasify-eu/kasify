package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BookingChannelSerializer::class)
enum class BookingChannel {
    Airbnb,
    Booking,
    Direct,
    Vrbo,
    Google,
}

object BookingChannelSerializer : KSerializer<BookingChannel> {
    override val descriptor = PrimitiveSerialDescriptor("BookingChannel", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BookingChannel {
        val value = decoder.decodeString()
        return BookingChannel.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid BookingChannel: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: BookingChannel,
    ) {
        encoder.encodeString(value.name)
    }
}
