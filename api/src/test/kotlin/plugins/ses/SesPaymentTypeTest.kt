package plugins.ses

import eu.kasify.db.model.PaymentType
import eu.kasify.plugins.ses.toSesTipoPago
import kotlin.test.Test
import kotlin.test.assertEquals

class SesPaymentTypeTest {
    @Test
    fun `maps every payment type to its SES tipoPago code`() {
        // Codes from documentation TIPO_PAGO catalog (catalog_tables_tipo_pago_response.xml).
        val expected =
            mapOf(
                PaymentType.Platform to "PLATF",
                PaymentType.Cash to "EFECT",
                PaymentType.CreditCard to "TARJT",
                PaymentType.BankTransfer to "TRANS",
                PaymentType.Mobile to "MOVIL",
                PaymentType.PayAtProperty to "DESTI",
                PaymentType.GiftCard to "TREG",
                PaymentType.Other to "OTRO",
            )

        // Guards against a new PaymentType being added without a SES mapping.
        assertEquals(PaymentType.entries.toSet(), expected.keys)
        expected.forEach { (type, code) -> assertEquals(code, type.toSesTipoPago()) }
    }
}
