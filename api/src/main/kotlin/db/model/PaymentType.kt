package eu.kasify.db.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * How a booking was paid for. These are generic, channel-agnostic values stored verbatim in the
 * `payment_type` PG enum — deliberately **not** the SES Hospedajes `tipoPago` codes. The mapping to
 * SES codes lives in the SES layer (`PaymentType.toSesTipoPago()`), so the domain stays decoupled
 * from registry specifics.
 *
 * [dbValue] is the snake_case string persisted in Postgres and exposed over the API; the enum name
 * can't be reused directly because multi-word values (e.g. `credit_card`) wouldn't round-trip.
 */
@Serializable(with = PaymentTypeSerializer::class)
enum class PaymentType(
    val dbValue: String,
) {
    Platform("platform"),
    Cash("cash"),
    CreditCard("credit_card"),
    BankTransfer("bank_transfer"),
    Mobile("mobile"),
    PayAtProperty("pay_at_property"),
    GiftCard("gift_card"),
    Other("other"),
    ;

    companion object {
        fun fromDb(value: String): PaymentType =
            entries.find { it.dbValue.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid PaymentType: $value")
    }
}

object PaymentTypeSerializer : KSerializer<PaymentType> {
    override val descriptor = PrimitiveSerialDescriptor("PaymentType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): PaymentType = PaymentType.fromDb(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: PaymentType,
    ) {
        encoder.encodeString(value.dbValue)
    }
}
