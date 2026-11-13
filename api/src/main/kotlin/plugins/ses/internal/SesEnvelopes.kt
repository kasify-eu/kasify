package eu.kasify.plugins.ses.internal

/**
 * Builds the outer SOAP envelopes for each SES operation.
 *
 * The output strings here are byte-equivalent to the inline templates that previously lived in
 * `SesHospedajesPlugin`, so the existing wire-level tests keep passing.
 */
internal object SesEnvelopes {
    fun catalogo(catalogName: String?): String {
        val catalogElement =
            if (catalogName != null) {
                "<catalogo>$catalogName</catalogo>"
            } else {
                "<catalogo></catalogo>"
            }
        return """
            |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
            |                  xmlns:com="http://www.soap.servicios.hospedajes.mir.es/comunicacion">
            |  <soapenv:Header/>
            |  <soapenv:Body>
            |    <com:catalogoRequest>
            |      <peticion>
            |        $catalogElement
            |      </peticion>
            |    </com:catalogoRequest>
            |  </soapenv:Body>
            |</soapenv:Envelope>
            """.trimMargin()
    }

    fun comunicacionAlta(
        arrendadorCode: String,
        aplicacion: String,
        tipoComunicacion: String,
        encodedSolicitud: String,
    ): String =
        comunicacion(
            arrendadorCode = arrendadorCode,
            aplicacion = aplicacion,
            tipoOperacion = "A",
            tipoComunicacion = tipoComunicacion,
            encodedSolicitud = encodedSolicitud,
        )

    fun comunicacionAnulacion(
        arrendadorCode: String,
        aplicacion: String,
        tipoComunicacion: String,
        encodedSolicitud: String,
    ): String =
        comunicacion(
            arrendadorCode = arrendadorCode,
            aplicacion = aplicacion,
            tipoOperacion = "B",
            tipoComunicacion = tipoComunicacion,
            encodedSolicitud = encodedSolicitud,
        )

    private fun comunicacion(
        arrendadorCode: String,
        aplicacion: String,
        tipoOperacion: String,
        tipoComunicacion: String,
        encodedSolicitud: String,
    ): String =
        """
        |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
        |                  xmlns:com="http://www.soap.servicios.hospedajes.mir.es/comunicacion">
        |  <soapenv:Header/>
        |  <soapenv:Body>
        |    <com:comunicacionRequest>
        |      <peticion>
        |        <cabecera>
        |          <codigoArrendador>${SesXml.escape(arrendadorCode)}</codigoArrendador>
        |          <aplicacion>${SesXml.escape(aplicacion)}</aplicacion>
        |          <tipoOperacion>${SesXml.escape(tipoOperacion)}</tipoOperacion>
        |          <tipoComunicacion>${SesXml.escape(tipoComunicacion)}</tipoComunicacion>
        |        </cabecera>
        |        <solicitud>$encodedSolicitud</solicitud>
        |      </peticion>
        |    </com:comunicacionRequest>
        |  </soapenv:Body>
        |</soapenv:Envelope>
        """.trimMargin()

    fun consultaLote(loteCodes: List<String>): String {
        val codigosXml = loteCodes.joinToString("\n") { "        <lote>${SesXml.escape(it)}</lote>" }
        return """
            |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
            |                  xmlns:com="http://www.soap.servicios.hospedajes.mir.es/comunicacion">
            |  <soapenv:Header/>
            |  <soapenv:Body>
            |    <com:consultaLoteRequest>
            |      <codigosLote>
            |$codigosXml
            |      </codigosLote>
            |    </com:consultaLoteRequest>
            |  </soapenv:Body>
            |</soapenv:Envelope>
            """.trimMargin()
    }
}
