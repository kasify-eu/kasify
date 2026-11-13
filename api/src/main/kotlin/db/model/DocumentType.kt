package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// SES Hospedajes (RD 933/2021) document types.
// Names match the lowercase values defined in the postgres `document_type` enum.
@Serializable(with = DocumentTypeSerializer::class)
enum class DocumentType {
    DNI,
    NIE,
    Passport,
    Other,
}

object DocumentTypeSerializer : KSerializer<DocumentType> {
    override val descriptor = PrimitiveSerialDescriptor("DocumentType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DocumentType {
        val value = decoder.decodeString()
        return DocumentType.entries.find { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid DocumentType: $value")
    }

    override fun serialize(
        encoder: Encoder,
        value: DocumentType,
    ) {
        encoder.encodeString(value.name)
    }
}
