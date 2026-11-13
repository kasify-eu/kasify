package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PropertyServiceSerializer::class)
enum class PropertyService {
    Cleaning,
    Maintenance,
    Pool,
    Gardening,
}

object PropertyServiceSerializer : KSerializer<PropertyService> {
    override val descriptor = PrimitiveSerialDescriptor("PropertyService", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PropertyService {
        val value = decoder.decodeString()
        return PropertyService.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid PropertyService: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: PropertyService,
    ) {
        encoder.encodeString(value.name.lowercase())
    }
}
