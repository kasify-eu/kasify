package eu.kasify.services.email

import eu.kasify.model.NotificationType
import eu.kasify.model.email.Locale
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailTemplateRendererTest {
    private val renderer = EmailTemplateRenderer()

    @Test
    fun `renders invitation template in english with all placeholders substituted`() {
        val rendered =
            renderer.render(
                templateName = "invitation",
                locale = Locale.EN,
                placeholders =
                    mapOf(
                        "inviter_name" to "Alice Owner",
                        "inviter_email" to "alice@example.com",
                        "property_name" to "Beach House",
                        "access_level_label" to "manager",
                        "accept_url" to "https://kasify.eu/properties?invitation=ABC123",
                        "dashboard_url" to "https://kasify.eu",
                        "invitation_code" to "ABC123",
                    ),
            )

        assertContains(rendered.subject, "Alice Owner")
        assertContains(rendered.subject, "Beach House")
        assertContains(rendered.html, "Beach House")
        assertContains(rendered.html, "https://kasify.eu/properties?invitation=ABC123")
        assertContains(rendered.html, "ABC123") // invitation code rendered in the "or use this code" block
        assertContains(rendered.html, "https://kasify.eu") // dashboard url rendered in the manual-entry instructions
        assertContains(rendered.text, "Alice Owner")
        assertContains(rendered.text, "Beach House")
        assertContains(rendered.text, "https://kasify.eu/properties?invitation=ABC123")
        assertContains(rendered.text, "ABC123")
        assertFalse("unsubstituted placeholder in html") { rendered.html.contains("{{") }
        assertFalse("unsubstituted placeholder in text") { rendered.text.contains("{{") }
        assertFalse("unsubstituted placeholder in subject") { rendered.subject.contains("{{") }
    }

    @Test
    fun `renders invitation template in spanish`() {
        val rendered =
            renderer.render(
                templateName = "invitation",
                locale = Locale.ES,
                placeholders =
                    mapOf(
                        "inviter_name" to "Ana Propietaria",
                        "property_name" to "Casa de la Playa",
                        "access_level_label" to "personal",
                        "accept_url" to "https://kasify.eu/properties?invitation=XYZ",
                        "dashboard_url" to "https://kasify.eu",
                        "invitation_code" to "XYZ987",
                    ),
            )

        assertContains(rendered.subject, "Ana Propietaria")
        assertContains(rendered.subject, "Casa de la Playa")
        assertContains(rendered.html, "Aceptar invitación")
        assertContains(rendered.html, "XYZ987")
        assertContains(rendered.html, "o usa este código")
        assertContains(rendered.text, "O usa este código")
        assertContains(rendered.text, "XYZ987")
    }

    @Test
    fun `html-escapes placeholder values to prevent injection from user-controlled names`() {
        val rendered =
            renderer.render(
                templateName = "invitation",
                locale = Locale.EN,
                placeholders =
                    mapOf(
                        "inviter_name" to "<script>alert('x')</script>",
                        "property_name" to "Beach & House",
                        "access_level_label" to "manager",
                        "accept_url" to "https://kasify.eu/accept",
                    ),
            )

        assertFalse("script tag must be escaped in html") { rendered.html.contains("<script>") }
        assertContains(rendered.html, "&lt;script&gt;")
        assertContains(rendered.html, "Beach &amp; House")
        // plain text version does NOT escape
        assertContains(rendered.text, "<script>alert('x')</script>")
        assertContains(rendered.text, "Beach & House")
    }

    @Test
    fun `missing placeholder substitutes empty string without leaving the raw token`() {
        val rendered =
            renderer.render(
                templateName = "invitation",
                locale = Locale.EN,
                placeholders =
                    mapOf(
                        "inviter_name" to "Alice",
                        "property_name" to "Beach House",
                        "accept_url" to "https://kasify.eu/accept",
                        // access_level_label intentionally omitted
                    ),
            )

        assertFalse("unsubstituted placeholder") { rendered.html.contains("{{access_level_label}}") }
        assertFalse("unsubstituted placeholder") { rendered.text.contains("{{access_level_label}}") }
    }

    @Test
    fun `renders ses_booking_rejected template in both locales`() {
        val placeholders =
            mapOf(
                "property_name" to "Casa de la Playa",
                "guest_name" to "Juan Pérez",
                "booking_from" to "2026-07-20",
                "booking_to" to "2026-07-25",
                "communication_type_label" to "Parte de viajeros (PV)",
                "error_message" to "Error de validación",
                "booking_url" to "https://kasify.eu/bookings?bookingId=abc",
            )

        val es = renderer.render(templateName = "ses_booking_rejected", locale = Locale.ES, placeholders = placeholders)
        assertContains(es.subject, "Casa de la Playa")
        assertContains(es.html, "Error de validación")
        assertContains(es.html, "https://kasify.eu/bookings?bookingId=abc")
        assertContains(es.text, "Juan Pérez")
        assertFalse("unsubstituted placeholder in html") { es.html.contains("{{") }
        assertFalse("unsubstituted placeholder in text") { es.text.contains("{{") }

        val en = renderer.render(templateName = "ses_booking_rejected", locale = Locale.EN, placeholders = placeholders)
        assertContains(en.subject, "Casa de la Playa")
        assertContains(en.html, "Error de validación")
        assertFalse("unsubstituted placeholder in html") { en.html.contains("{{") }
    }

    @Test
    fun `every notification type has email templates in both locales`() {
        NotificationType.entries.forEach { type ->
            Locale.entries.forEach { locale ->
                val rendered = renderer.render(templateName = type.dbValue, locale = locale, placeholders = emptyMap())
                assertTrue("empty subject for ${type.dbValue}/$locale") { rendered.subject.isNotBlank() }
                assertTrue("empty html for ${type.dbValue}/$locale") { rendered.html.isNotBlank() }
                assertTrue("empty text for ${type.dbValue}/$locale") { rendered.text.isNotBlank() }
            }
        }
    }

    @Test
    fun `subject trims trailing whitespace and newline`() {
        val rendered =
            renderer.render(
                templateName = "invitation",
                locale = Locale.EN,
                placeholders =
                    mapOf(
                        "inviter_name" to "Alice",
                        "property_name" to "Beach House",
                        "access_level_label" to "manager",
                        "accept_url" to "https://kasify.eu/accept",
                    ),
            )

        assertEquals(rendered.subject, rendered.subject.trim())
    }
}
