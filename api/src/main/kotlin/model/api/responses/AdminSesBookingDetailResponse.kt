package eu.kasify.model.api.responses

import eu.kasify.model.api.Booking
import eu.kasify.model.api.SesHospedajesSubmission
import kotlinx.serialization.Serializable

@Serializable
data class AdminSesBookingDetailResponse(
    val booking: Booking,
    val submissions: List<SesHospedajesSubmission>,
)
