package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CrewRoleSerializer::class)
enum class CrewRole {
    Cleaning,
    Maintenance,
    Pool,
    Gardening,
}

object CrewRoleSerializer : KSerializer<CrewRole> {
    override val descriptor = PrimitiveSerialDescriptor("CrewRole", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): CrewRole {
        val value = decoder.decodeString()
        return CrewRole.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid CrewRole: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: CrewRole,
    ) {
        encoder.encodeString(value.name)
    }
}
