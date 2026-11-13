package eu.kasify.plugins.ses.internal

import org.junit.Test
import org.xml.sax.SAXParseException
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SesXmlTest {
    @Test
    fun `parses a regular namespaced SOAP response`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
              <soapenv:Body>
                <ns2:respuesta xmlns:ns2="http://www.neg.hospedajes.mir.es/altaParteHospedaje">
                  <codigo>0</codigo>
                  <descripcion>Ok &amp; done</descripcion>
                </ns2:respuesta>
              </soapenv:Body>
            </soapenv:Envelope>
            """.trimIndent()

        val document = SesXml.parse(xml)

        assertEquals("0", SesXml.childText(document.documentElement, "codigo"))
        assertEquals("Ok & done", SesXml.childText(document.documentElement, "descripcion"))
    }

    @Test
    fun `rejects a DOCTYPE with an external file entity`() {
        val secret = File.createTempFile("ses-xxe", ".txt")
        try {
            secret.writeText("TOP-SECRET-CONTENT")
            val xml =
                """
                <?xml version="1.0"?>
                <!DOCTYPE r [<!ENTITY xxe SYSTEM "${secret.toURI()}">]>
                <r><codigoComunicacion>&xxe;</codigoComunicacion></r>
                """.trimIndent()

            val error = assertFailsWith<SAXParseException> { SesXml.parse(xml) }

            assertFalse(error.message.orEmpty().contains("TOP-SECRET-CONTENT"))
        } finally {
            secret.delete()
        }
    }

    @Test
    fun `rejects a DOCTYPE referencing an external DTD`() {
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE r SYSTEM "file:///nonexistent/ses.dtd">
            <r/>
            """.trimIndent()

        assertFailsWith<SAXParseException> { SesXml.parse(xml) }
    }

    @Test
    fun `rejects a DOCTYPE with internal entity expansion`() {
        val xml =
            """
            <?xml version="1.0"?>
            <!DOCTYPE r [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]>
            <r>&b;</r>
            """.trimIndent()

        assertFailsWith<SAXParseException> { SesXml.parse(xml) }
    }
}
