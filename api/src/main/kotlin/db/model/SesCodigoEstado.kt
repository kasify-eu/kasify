package eu.kasify.db.model

import org.postgresql.util.PGobject

// Entry names mirror the official SES status codes (<name>_<codigo>).
@Suppress("ktlint:standard:enum-entry-name-case")
enum class SesCodigoEstado(
    val codigo: Int,
    val dbValue: String,
) {
    TramitadoSinErrores_1(1, "1_tramitado_sin_errores"),
    ErroresCabeceraFormato_2(2, "2_errores_cabecera_formato"),
    ErrorInesperado_3(3, "3_error_inesperado"),
    EnProceso_4(4, "4_en_proceso"),
    Pendiente_5(5, "5_pendiente"),
    TramitadoConErroresParciales_6(6, "6_tramitado_con_errores_parciales"),
    CodigoDesconocido_99(99, "99_codigo_desconocido"),
    ;

    fun isTerminal(): Boolean = this != EnProceso_4 && this != Pendiente_5

    fun isSuccess(): Boolean = this == TramitadoSinErrores_1

    fun isErrorTerminal(): Boolean =
        this == ErroresCabeceraFormato_2 ||
            this == ErrorInesperado_3 ||
            this == TramitadoConErroresParciales_6 ||
            this == CodigoDesconocido_99

    companion object {
        fun fromCodigo(codigo: Int): SesCodigoEstado? = entries.firstOrNull { it.codigo == codigo }

        fun fromCodigoOrUnknown(codigo: Int): SesCodigoEstado = fromCodigo(codigo) ?: CodigoDesconocido_99

        fun fromDb(value: String): SesCodigoEstado =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown ses_codigo_estado db value: $value")
    }
}

internal class SesCodigoEstadoPGEnum(
    value: SesCodigoEstado?,
) : PGobject() {
    init {
        this.value = value?.dbValue
        this.type = "ses_codigo_estado"
    }
}
