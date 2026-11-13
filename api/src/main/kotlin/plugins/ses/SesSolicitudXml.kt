package eu.kasify.plugins.ses

import eu.kasify.plugins.ses.internal.SesPvSolicitudWriter
import eu.kasify.plugins.ses.internal.SesRhSolicitudWriter

/**
 * Plain (pre-compression) solicitud XML for a communication request — the exact content SES
 * receives inside the zipped `<solicitud>` element. Exposed so callers can archive what was
 * communicated (libro registro / audit trail) without reaching into `internal/`.
 */
fun SesParteViajerosRequest.solicitudXml(): String = SesPvSolicitudWriter.buildXml(this)

fun SesReservaHospedajeRequest.solicitudXml(): String = SesRhSolicitudWriter.buildXml(this)
