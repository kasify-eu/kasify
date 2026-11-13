package eu.kasify.services.notifications

import eu.kasify.model.email.Locale

/**
 * Renders a plain-text Telegram message from a template under `resources/telegram/`. Templates are
 * one file per (name, locale): `<name>.<locale>.txt`, with `{{placeholder}}` substitution. Falls
 * back to the English variant when a locale file is missing. Unlike the email renderer there is no
 * HTML escaping or subject line — Telegram messages are plain text.
 */
open class TelegramMessageRenderer {
    open fun render(
        templateName: String,
        locale: Locale,
        placeholders: Map<String, String>,
    ): String {
        val raw =
            readResource("telegram/$templateName.${locale.tag}.txt")
                ?: readResource("telegram/$templateName.${Locale.EN.tag}.txt")
                ?: error("Missing Telegram template: $templateName.<locale>.txt")

        return PLACEHOLDER_REGEX
            .replace(raw) { match -> placeholders[match.groupValues[1]] ?: "" }
            .trim()
    }

    private fun readResource(path: String): String? =
        this::class.java.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }

    companion object {
        private val PLACEHOLDER_REGEX = Regex("""\{\{\s*([a-zA-Z0-9_]+)\s*\}\}""")
    }
}
