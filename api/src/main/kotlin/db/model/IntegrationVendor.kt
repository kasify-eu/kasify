package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = IntegrationVendorSerializer::class)
enum class IntegrationVendor {
    Nuki,
    Unifi,
    SesHospedajes,
}

object IntegrationVendorSerializer : KSerializer<IntegrationVendor> {
    override val descriptor = PrimitiveSerialDescriptor("IntegrationVendor", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): IntegrationVendor {
        val value = decoder.decodeString()
        return IntegrationVendor.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid IntegrationVendor: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: IntegrationVendor,
    ) {
        encoder.encodeString(value.name.lowercase())
    }
}
