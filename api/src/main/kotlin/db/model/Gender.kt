package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = GenderSerializer::class)
enum class Gender {
    Male,
    Female,
    Other,
}

object GenderSerializer : KSerializer<Gender> {
    override val descriptor = PrimitiveSerialDescriptor("Gender", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Gender {
        val value = decoder.decodeString()
        return Gender.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid Gender: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: Gender,
    ) {
        encoder.encodeString(value.name)
    }
}
