package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = SmartlockVendorSerializer::class)
enum class SmartlockVendor {
    Nuki,
    Unifi,
    Tedee,
    Yale,
}

object SmartlockVendorSerializer : KSerializer<SmartlockVendor> {
    override val descriptor = PrimitiveSerialDescriptor("SmartlockVendor", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): SmartlockVendor {
        val value = decoder.decodeString()
        return SmartlockVendor.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid SmartlockVendor: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: SmartlockVendor,
    ) {
        encoder.encodeString(value.name)
    }
}
