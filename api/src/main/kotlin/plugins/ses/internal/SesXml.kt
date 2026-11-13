package eu.kasify.plugins.ses.internal

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.OffsetDateTime
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lightweight XML utilities shared by the SES request writers and response parsers.
 *
 * Intentionally kept tiny — every method here is mechanical and namespace-aware so the rest
 * of the SES code can stop juggling DOM boilerplate.
 */
internal object SesXml {
    /**
     * SES responses are untrusted network input, so the parser is hardened against XXE:
     * DOCTYPE declarations are rejected outright (SES SOAP responses never carry one) and
     * every external DTD / entity / schema / XInclude resolution path is disabled as
     * defense in depth.
     */
    private val documentBuilderFactory: DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    fun parse(xml: String): Document = documentBuilderFactory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))

    fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    /**
     * Returns the trimmed text content of the first descendant of [parent] with the given local
     * name (any namespace), or `null` if no such descendant exists.
     */
    fun childText(
        parent: Element,
        localName: String,
    ): String? {
        val children = parent.getElementsByTagNameNS("*", localName)
        return if (children.length > 0) children.item(0).textContent.trim() else null
    }

    /**
     * Parses a date-time from SES.
     *
     * In practice SES sends `fechaPeticion` / `fechaProcesamiento` as `yyyy-MM-dd HH:mm:ss`
     * (space separator) rather than ISO-8601 `yyyy-MM-ddTHH:mm:ss`. We normalize the first space
     * to `T` and accept either a `LocalDateTime` or an `OffsetDateTime` if a timezone is appended.
     */
    fun parseDateTime(value: String): LocalDateTime {
        val normalized = value.trim().replaceFirst(' ', 'T')
        return try {
            LocalDateTime.parse(normalized)
        } catch (_: Exception) {
            OffsetDateTime.parse(normalized).toLocalDateTime()
        }
    }
}
