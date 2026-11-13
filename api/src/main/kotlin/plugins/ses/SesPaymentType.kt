package eu.kasify.plugins.ses

import eu.kasify.db.model.PaymentType

/**
 * Translation layer between the domain's generic [PaymentType] and the SES Hospedajes `tipoPago`
 * catalog codes (TIPO_PAGO). Kept here, in the SES package, so the domain enum stays decoupled from
 * registry specifics — see `catalog_tables_tipo_pago_response.xml` for the source of these codes.
 */
fun PaymentType.toSesTipoPago(): String =
    when (this) {
        PaymentType.Platform -> "PLATF"
        PaymentType.Cash -> "EFECT"
        PaymentType.CreditCard -> "TARJT"
        PaymentType.BankTransfer -> "TRANS"
        PaymentType.Mobile -> "MOVIL"
        PaymentType.PayAtProperty -> "DESTI"
        PaymentType.GiftCard -> "TREG"
        PaymentType.Other -> "OTRO"
    }
