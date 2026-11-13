package eu.kasify.model.email

import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleTest {
    @Test
    fun `fromTag matches exact language tags`() {
        assertEquals(Locale.EN, Locale.fromTag("en"))
        assertEquals(Locale.ES, Locale.fromTag("es"))
        assertEquals(Locale.ES, Locale.fromTag("ES"))
    }

    @Test
    fun `fromTag tolerates region-qualified BCP-47 tags`() {
        assertEquals(Locale.ES, Locale.fromTag("es-ES"))
        assertEquals(Locale.ES, Locale.fromTag("es_419"))
        assertEquals(Locale.EN, Locale.fromTag("en-US"))
    }

    @Test
    fun `fromTag falls back to EN for null or unknown tags`() {
        assertEquals(Locale.EN, Locale.fromTag(null))
        assertEquals(Locale.EN, Locale.fromTag("fr"))
        assertEquals(Locale.EN, Locale.fromTag(""))
    }
}
