package eu.kasify.plugins.ses

/**
 * SES Hospedajes environment endpoints. Production targets the live MIR service; TEST targets
 * the pre-production endpoint that mirrors the same API but is wired to a sandbox dataset.
 */
enum class SesEnvironment(
    val baseUrl: String,
) {
    PRODUCTION("https://hospedajes.ses.mir.es/hospedajes-web/ws/v1/comunicacion"),
    TEST("https://hospedajes.pre-ses.mir.es/hospedajes-web/ws/v1/comunicacion"),
    ;

    companion object {
        /**
         * Parses the legacy string-typed environment used in DB/config rows.
         * Anything other than the case-insensitive `"production"` falls through to [TEST] —
         * the same defaulting behavior the original `when` statement had.
         */
        fun fromString(value: String?): SesEnvironment =
            when (value?.lowercase()) {
                "production" -> PRODUCTION
                else -> TEST
            }
    }
}
