package eu.kasify.model.email

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Locale(
    val tag: String,
) {
    @SerialName("en")
    EN("en"),

    @SerialName("es")
    ES("es"),
    ;

    companion object {
        /**
         * Resolves a language tag to a [Locale], tolerating region-qualified BCP-47 tags: only the
         * primary subtag is matched, so `es`, `es-ES` and `es_419` all map to [ES]. Unknown or null
         * tags fall back to [EN].
         */
        fun fromTag(tag: String?): Locale {
            val primary = tag?.lowercase()?.substringBefore('-')?.substringBefore('_')
            return entries.firstOrNull { it.tag == primary } ?: EN
        }
    }
}
