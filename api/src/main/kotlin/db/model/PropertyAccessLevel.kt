package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PropertyAccessLevelSerializer::class)
enum class PropertyAccessLevel(
    val dbValue: String,
) {
    Owner("owner"),
    Manager("manager"),
    Staff("staff"),
    Contractor("contractor"),
    ;

    companion object {
        fun fromDb(value: String): PropertyAccessLevel =
            entries.find { it.name.equals(value, ignoreCase = true) || it.dbValue == value }
                ?: Contractor
    }
}

object PropertyAccessLevelSerializer : KSerializer<PropertyAccessLevel> {
    override val descriptor = PrimitiveSerialDescriptor("PropertyAccessLevel", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PropertyAccessLevel {
        val value = decoder.decodeString()
        return PropertyAccessLevel.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid PropertyAccessLevel: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: PropertyAccessLevel,
    ) {
        encoder.encodeString(value.name)
    }
}
