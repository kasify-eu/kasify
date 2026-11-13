package eu.kasify

import eu.kasify.model.api.requests.AddSmartlockRequest
import eu.kasify.model.api.requests.BookingData
import eu.kasify.model.api.requests.GuestRegistrationRequest
import eu.kasify.model.api.requests.NewPropertyRequest
import eu.kasify.model.api.requests.NukiIntegrationRequest
import eu.kasify.model.api.requests.PropertyPatch
import eu.kasify.model.api.requests.SesHospedajesIntegrationRequest
import eu.kasify.model.api.requests.SignupRequest
import eu.kasify.model.api.requests.UnifiIntegrationRequest
import eu.kasify.model.api.requests.validate
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation

fun Application.configureValidation() {
    install(RequestValidation) {
        // Property validation
        validate<NewPropertyRequest> { request -> request.validate() }
        validate<PropertyPatch> { request -> request.validate() }

        // Booking validation
        validate<BookingData> { request -> request.validate() }

        // Smart lock validation
        validate<AddSmartlockRequest> { request -> request.validate() }

        // Integration validation
        validate<NukiIntegrationRequest> { request -> request.validate() }
        validate<UnifiIntegrationRequest> { request -> request.validate() }
        validate<SesHospedajesIntegrationRequest> { request -> request.validate() }

        // Signup validation
        validate<SignupRequest> { request -> request.validate() }

        // Guest check-in validation
        validate<GuestRegistrationRequest> { request -> request.validate() }
    }
}
