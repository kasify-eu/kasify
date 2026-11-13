package eu.kasify.services.email

import eu.kasify.model.email.Locale

data class RenderedTemplate(
    val subject: String,
    val html: String,
    val text: String,
)

/**
 * Loads email templates from classpath resources under `email/` and substitutes `{{placeholder}}`
 * tokens with caller-supplied values. Each email is three files: `<name>.<locale>.subject.txt`,
 * `<name>.<locale>.html`, and `<name>.<locale>.txt`. Falls back to the English variant when a
 * locale file is missing. HTML-escapes placeholder values in `.html` to prevent injection from
 * user-controlled content (e.g. inviter or property names).
 */
open class EmailTemplateRenderer {
    open fun render(
        templateName: String,
        locale: Locale,
        placeholders: Map<String, String>,
    ): RenderedTemplate {
        val subject =
            loadAndSubstitute(templateName, locale, "subject.txt", placeholders, htmlEscape = false)
                ?: error("Missing email template: $templateName.<locale>.subject.txt")
        val text =
            loadAndSubstitute(templateName, locale, "txt", placeholders, htmlEscape = false)
                ?: error("Missing email template: $templateName.<locale>.txt")
        val html =
            loadAndSubstitute(templateName, locale, "html", placeholders, htmlEscape = true)
                ?: error("Missing email template: $templateName.<locale>.html")

        return RenderedTemplate(
            subject = subject.trim(),
            html = html,
            text = text,
        )
    }

    private fun loadAndSubstitute(
        templateName: String,
        locale: Locale,
        suffix: String,
        placeholders: Map<String, String>,
        htmlEscape: Boolean,
    ): String? {
        val raw =
            readResource("email/$templateName.${locale.tag}.$suffix")
                ?: readResource("email/$templateName.${Locale.EN.tag}.$suffix")
                ?: return null

        return PLACEHOLDER_REGEX.replace(raw) { match ->
            val key = match.groupValues[1]
            val value = placeholders[key] ?: ""
            if (htmlEscape) htmlEscape(value) else value
        }
    }

    private fun readResource(path: String): String? =
        this::class.java.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }

    companion object {
        private val PLACEHOLDER_REGEX = Regex("""\{\{\s*([a-zA-Z0-9_]+)\s*\}\}""")

        fun htmlEscape(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}
