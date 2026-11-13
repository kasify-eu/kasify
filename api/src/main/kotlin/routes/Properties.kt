package eu.kasify.routes
import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.GeoRepository
import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.PropertyChannelIntegrationsRepository
import eu.kasify.db.SmartlocksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.db.model.BookingChannel
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.api.Calendar
import eu.kasify.model.api.FileResponse
import eu.kasify.model.api.Property
import eu.kasify.model.api.SecuritySerializer
import eu.kasify.model.api.SmartLockInfo
import eu.kasify.model.api.requests.NewPropertyRequest
import eu.kasify.model.api.requests.PropertyPatch
import eu.kasify.model.api.responses.PropertiesResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.routes.extensions.getPathProperty
import eu.kasify.routes.extensions.isAssignableToProperty
import eu.kasify.routes.extensions.respondNotFound
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.services.FileService
import eu.kasify.services.resolvePropertyGeo
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.toByteArray
import io.perracodex.exposed.pagination.Pageable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configurePropertiesRouting() {
    val service by inject<PropertiesRepository>()
    val authorizationService by inject<AuthorizationService>()
    val integrationsRepository by inject<IntegrationsRepository>()
    val smartlocksRepository by inject<SmartlocksRepository>()
    val propertyChannelIntegrationsRepository by inject<PropertyChannelIntegrationsRepository>()
    val propertyAccessRepository by inject<PropertyAccessRepository>()
    val usersRepository by inject<UsersRepository>()
    val fileService by inject<FileService>()
    val geoRepository by inject<GeoRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/properties") {
                val user = call.getKasifyUser()
                val pageable: Pageable = call.getPageableWithLimit()

                val properties = service.getProperties(user, pageable)
                val countries =
                    geoRepository.getCountriesByIds(
                        properties.content.map { it.countryId }.toSet(),
                    )
                val sesEnabledAccountIds =
                    integrationsRepository.getAccountIdsWithEnabledSesIntegration(
                        properties.content.map { it.accountId }.toSet(),
                    )
                // Gate states of the properties' assigned integrations (one batched query) — drives
                // the derived ses_status (paused_credentials / paused_config / disabled / ok).
                val integrationStates =
                    integrationsRepository.getSesIntegrationGateStates(
                        properties.content.mapNotNull { it.sesIntegrationId }.toSet(),
                    )
                val accessByProperty =
                    authorizationService.getAccessForLoadedProperties(user, properties.content)
                val apiProperties =
                    properties.content.map { property ->
                        Property.from(
                            property,
                            role = accessByProperty[property.id]?.role,
                            country = countries[property.countryId],
                            sesIntegrationEnabled = property.accountId in sesEnabledAccountIds,
                            assignedIntegrationState = property.sesIntegrationId?.let { integrationStates[it] },
                        )
                    }
                // Each property is filtered by the caller's access level on that property, so
                // manager-only fields (SES config, rooms, etc.) are stripped for staff/contractors.
                val filteredProperties =
                    SecuritySerializer.serializeWithPropertyAccess(
                        items = apiProperties,
                        getPropertyId = { requireNotNull(it.id) },
                        accessByProperty = accessByProperty,
                    )
                call.respond(
                    buildJsonObject {
                        put("properties", filteredProperties)
                        put("page", JsonPrimitive(properties.details.pageIndex))
                        put("size", JsonPrimitive(properties.details.elementsPerPage))
                        put("total", JsonPrimitive(properties.details.totalElements))
                    },
                )
            }.describe {
                tag("Properties")
                summary = "List properties"
                parameters {
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated properties the caller owns or has crew access to"
                        schema = jsonSchema<PropertiesResponse>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }

            post("/properties") {
                val user = call.getKasifyUser()
                val propertyRequest = call.receive<NewPropertyRequest>()

                requireEstablishmentCodeNotArrendador(
                    propertyRequest.sesEstablishmentCode,
                    user.accountId,
                    integrationsRepository,
                )
                requireValidSesAssignment(
                    establishmentCode = propertyRequest.sesEstablishmentCode,
                    requestedIntegrationId = propertyRequest.sesIntegrationId,
                    currentIntegrationId = null,
                    accountId = user.accountId,
                    integrationsRepository = integrationsRepository,
                )

                val geo = resolvePropertyGeo(propertyRequest, geoRepository)
                val tzone = "Europe/Madrid"

                val result =
                    service.createProperty(
                        user = user,
                        property = propertyRequest,
                        countryId = geo.countryId,
                        tzone = tzone,
                        cityName = geo.cityName,
                        provinceId = geo.provinceId,
                        cityId = geo.cityId,
                    )
                propertyRequest.airbnbIcalUrl?.let {
                    propertyChannelIntegrationsRepository.createPropertyChannelIntegration(
                        it,
                        result.id,
                        BookingChannel.Airbnb,
                    )
                }
                propertyRequest.bookingIcalUrl?.let {
                    propertyChannelIntegrationsRepository.createPropertyChannelIntegration(
                        it,
                        result.id,
                        BookingChannel.Booking,
                    )
                }

                val country = geoRepository.getCountryById(geo.countryId)
                call.respond(HttpStatusCode.Created, Property.from(result, country = country))
            }.describe {
                tag("Properties")
                summary = "Create property"
                requestBody {
                    description =
                        "New property fields, optionally including Airbnb and/or Booking.com iCal URLs to import bookings"
                    required = true
                    schema = jsonSchema<NewPropertyRequest>()
                }
                responses {
                    HttpStatusCode.Created {
                        description = "The created property"
                        schema = jsonSchema<Property>()
                    }
                    HttpStatusCode.BadRequest {
                        description =
                            "Invalid request, e.g. the SES establishment code equals the arrendador code " +
                            "(`property.establishment_code_matches_arrendador`) or a code is set on an " +
                            "unassigned property without `ses_integration_id` " +
                            "(`ses_hospedajes.integration_required`)"
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                }
            }

            get("/properties/{id}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Access to property denied.")
                }

                // Fetch the calendars (PropertyChannelIntegration) for the property
                val integrations = propertyChannelIntegrationsRepository.getIntegrationsByPropertyId(property.id)
                val calendars = integrations.map { Calendar.from(it) }

                // Fetch smart locks for this property
                val doors = smartlocksRepository.getSmartlocksByProperty(property.id)
                val smartLocks = doors.map { SmartLockInfo.from(it) }

                // Fetch property members for this property
                val crew = propertyAccessRepository.getPropertyMembersByPropertyId(property.id)

                // Fetch owners (account members) for this property
                val owners = usersRepository.getUsersByAccountId(property.accountId)

                val team = owners + crew
                val defaultCleaner = team.find { it.id == property.defaultCleanerId }
                val country = geoRepository.getCountryById(property.countryId)
                val sesIntegrationEnabled =
                    integrationsRepository
                        .getAccountIdsWithEnabledSesIntegration(setOf(property.accountId))
                        .isNotEmpty()
                val assignedIntegrationState =
                    property.sesIntegrationId?.let {
                        integrationsRepository.getSesIntegrationGateStates(setOf(it))[it]
                    }
                val apiProperty =
                    Property.from(
                        property,
                        calendars,
                        smartLocks,
                        team,
                        access.role,
                        defaultCleaner,
                        country = country,
                        sesIntegrationEnabled = sesIntegrationEnabled,
                        assignedIntegrationState = assignedIntegrationState,
                    )
                call.respond(SecuritySerializer.serialize(apiProperty, access))
            }.describe {
                tag("Properties")
                summary = "Get property"
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The property with its calendars, smart locks, team, and default cleaner. Sensitive fields are redacted for non-owner roles."
                        schema = jsonSchema<Property>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks access to the property" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            put("/properties/{id}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                authorizationService.requireOwner(user, property)

                val propertyRequest = call.receive<NewPropertyRequest>()

                requireValidDefaultCleaner(
                    propertyRequest.defaultCleanerId,
                    property,
                    usersRepository,
                    propertyAccessRepository,
                )
                requireEstablishmentCodeNotArrendador(
                    propertyRequest.sesEstablishmentCode,
                    property.accountId,
                    integrationsRepository,
                )
                requireValidSesAssignment(
                    establishmentCode = propertyRequest.sesEstablishmentCode,
                    requestedIntegrationId = propertyRequest.sesIntegrationId,
                    currentIntegrationId = property.sesIntegrationId,
                    accountId = property.accountId,
                    integrationsRepository = integrationsRepository,
                )

                val geo = resolvePropertyGeo(propertyRequest, geoRepository)
                val updatedProperty =
                    service.updateProperty(
                        id = property.id,
                        property = propertyRequest,
                        countryId = geo.countryId,
                        cityName = geo.cityName,
                        provinceId = geo.provinceId,
                        cityId = geo.cityId,
                    )
                        ?: return@put call.respondNotFound("Property not found")

                if (propertyRequest.airbnbIcalUrl != null) {
                    propertyChannelIntegrationsRepository.upsertPropertyChannelIntegration(
                        propertyRequest.airbnbIcalUrl,
                        property.id,
                        BookingChannel.Airbnb,
                    )
                } else {
                    propertyChannelIntegrationsRepository.deletePropertyChannelIntegration(
                        property.id,
                        BookingChannel.Airbnb,
                    )
                }
                if (propertyRequest.bookingIcalUrl != null) {
                    propertyChannelIntegrationsRepository.upsertPropertyChannelIntegration(
                        propertyRequest.bookingIcalUrl,
                        property.id,
                        BookingChannel.Booking,
                    )
                } else {
                    propertyChannelIntegrationsRepository.deletePropertyChannelIntegration(
                        property.id,
                        BookingChannel.Booking,
                    )
                }
                val country = geoRepository.getCountryById(geo.countryId)
                call.respond(Property.from(updatedProperty, country = country))
            }.describe {
                tag("Properties")
                summary = "Update property"
                description = "Replaces mutable property fields and re-syncs the Airbnb and Booking.com iCal integrations based on the supplied URLs (set to null to remove an integration)."
                parameters {
                    path("id") { description = "Property id" }
                }
                requestBody {
                    description = "Updated property fields"
                    required = true
                    schema = jsonSchema<NewPropertyRequest>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The updated property"
                        schema = jsonSchema<Property>()
                    }
                    HttpStatusCode.BadRequest {
                        description =
                            "Invalid request, e.g. the SES establishment code equals the arrendador code " +
                            "(`property.establishment_code_matches_arrendador`), a code is set on an " +
                            "unassigned property without `ses_integration_id` " +
                            "(`ses_hospedajes.integration_required`), or `default_cleaner_id` is changed " +
                            "to a user who is not an owner or crew member of the property " +
                            "(`property.default_cleaner_not_in_property`)"
                    }
                    HttpStatusCode.Conflict {
                        description =
                            "The property is already configured on another SES integration " +
                            "(`ses_hospedajes.property_already_configured`) — clear its establishment " +
                            "code there first (reassignment is two-step)."
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            patch("/properties/{id}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                authorizationService.requireOwner(user, property)

                val patchRequest = call.receive<PropertyPatch>()

                requireValidDefaultCleaner(
                    patchRequest.defaultCleanerId,
                    property,
                    usersRepository,
                    propertyAccessRepository,
                )
                requireEstablishmentCodeNotArrendador(
                    patchRequest.sesEstablishmentCode,
                    property.accountId,
                    integrationsRepository,
                )
                requireValidSesAssignment(
                    establishmentCode = patchRequest.sesEstablishmentCode,
                    requestedIntegrationId = patchRequest.sesIntegrationId,
                    currentIntegrationId = property.sesIntegrationId,
                    accountId = property.accountId,
                    integrationsRepository = integrationsRepository,
                )

                val updatedProperty =
                    service.patchProperty(id = property.id, patch = patchRequest)
                        ?: return@patch call.respondNotFound("Property not found")

                val country = geoRepository.getCountryById(updatedProperty.countryId)
                call.respond(Property.from(updatedProperty, country = country))
            }.describe {
                tag("Properties")
                summary = "Partially update property"
                description =
                    "Applies a JSON Merge Patch–style partial update. Only fields supplied in the " +
                    "body are changed; absent fields are left untouched. Geo fields " +
                    "(country/province/city) and `airbnb_ical_url` are not supported here — " +
                    "use PUT for those."
                parameters {
                    path("id") { description = "Property id" }
                }
                requestBody {
                    description = "Subset of mutable property fields to update"
                    required = true
                    schema = jsonSchema<PropertyPatch>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The updated property"
                        schema = jsonSchema<Property>()
                    }
                    HttpStatusCode.BadRequest {
                        description =
                            "Invalid request, e.g. the SES establishment code equals the arrendador code " +
                            "(`property.establishment_code_matches_arrendador`), a code is set on an " +
                            "unassigned property without `ses_integration_id` " +
                            "(`ses_hospedajes.integration_required`), or `default_cleaner_id` is changed " +
                            "to a user who is not an owner or crew member of the property " +
                            "(`property.default_cleaner_not_in_property`)"
                    }
                    HttpStatusCode.Conflict {
                        description =
                            "The property is already configured on another SES integration " +
                            "(`ses_hospedajes.property_already_configured`) — clear its establishment " +
                            "code there first (reassignment is two-step)."
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            delete("/properties/{id}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                authorizationService.requireOwner(user, property)

                val deleted = service.deleteProperty(property.id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respondNotFound("Property not found")
                }
            }.describe {
                tag("Properties")
                summary = "Delete property"
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Property deleted" }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            post("/properties/{id}/leave") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                val access = authorizationService.getAccess(user, property)

                // Owners (account members) can't leave — they delete instead.
                if (access.isAccountMember) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException(
                            "Owners cannot leave a property.",
                            code = "property.owner_cannot_leave",
                        )
                }
                // Must hold a crew grant to leave.
                if (!access.hasAccess) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Access to property denied.")
                }

                val revoked = propertyAccessRepository.revoke(user.userId, property.id)
                if (!revoked) {
                    return@post call.respondNotFound("No crew access to this property")
                }

                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("Properties")
                summary = "Leave property"
                description = "Removes the authenticated caller's own crew access to the property. Only non-owner crew members (manager, staff, contractor) may leave; account owners must delete the property instead."
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Caller's crew access removed" }
                    HttpStatusCode.Forbidden { description = "Caller has no access to the property" }
                    HttpStatusCode.Forbidden {
                        description = "Caller is the property owner and cannot leave. Returns `property.owner_cannot_leave`"
                    }
                    HttpStatusCode.NotFound { description = "Property not found, or caller has no crew access to remove" }
                }
            }

            post("/properties/{id}/cover-image") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Owner or manager access required.")
                }

                val multipart = call.receiveMultipart()
                var fileBytes: ByteArray? = null
                var contentType: String? = null
                var originalFilename: String? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem && part.name == "file") {
                        contentType = part.contentType?.toString()
                        originalFilename = part.originalFileName ?: "cover"
                        fileBytes = part.provider().toByteArray()
                    }
                    part.dispose()
                }

                if (fileBytes == null || contentType == null) {
                    throw BadParamRequestException(
                        "No file provided. Send a multipart form with a 'file' field.",
                        code = "file.not_provided",
                    )
                }

                val fileRecord =
                    fileService.uploadPropertyCoverImage(
                        user = user,
                        property = property,
                        fileBytes = fileBytes,
                        contentType = contentType!!,
                        originalFilename = originalFilename!!,
                    )

                call.respond(HttpStatusCode.OK, FileResponse.from(fileRecord))
            }.describe {
                tag("Properties")
                summary = "Upload cover image"
                description = "Multipart upload. Expects a single `file` part containing the image bytes. Only owners and managers may update the cover image for a property."
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The stored file metadata"
                        schema = jsonSchema<FileResponse>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "No `file` part in the multipart body. Returns `file.not_provided`"
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not an owner or manager" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            delete("/properties/{id}/cover-image") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(service)

                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && access.accessLevel != PropertyAccessLevel.Manager) {
                    throw eu.kasify.routes.exceptions
                        .ForbiddenException("Owner or manager access required.")
                }

                fileService.deletePropertyCoverImage(property)
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("Properties")
                summary = "Delete cover image"
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Cover image removed" }
                    HttpStatusCode.Forbidden { description = "Caller is not an owner or manager" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }
        }
    }
}

/**
 * Rejects a request that changes the default cleaner to a user who is neither an owner (a user of
 * the property's account) nor a crew member of [property]. Absent or unchanged values pass, so a
 * full PUT echoing the current default cleaner keeps working even if that user has left the crew.
 */
private suspend fun requireValidDefaultCleaner(
    requestedCleanerId: Uuid?,
    property: eu.kasify.model.Property,
    usersRepository: UsersRepository,
    propertyAccessRepository: PropertyAccessRepository,
) {
    if (requestedCleanerId == null || requestedCleanerId == property.defaultCleanerId) {
        return
    }
    if (!isAssignableToProperty(requestedCleanerId, property, usersRepository, propertyAccessRepository)) {
        throw BadParamRequestException(
            Errors.ERROR_PROPERTY_DEFAULT_CLEANER_NOT_IN_PROPERTY,
            code = Errors.CODE_PROPERTY_DEFAULT_CLEANER_NOT_IN_PROPERTY,
        )
    }
}

/**
 * Enforces the property↔integration assignment rules (one SES integration per property, strict):
 *
 * - `ses_integration_id` is only accepted together with a non-blank `ses_establishment_code`.
 * - A non-blank code with an integration id: the id must belong to the caller's account (404),
 *   and a property already assigned to a *different* integration is rejected with
 *   409 `ses_hospedajes.property_already_configured` — reassignment is two-step (clear the code
 *   on the old integration first, which unassigns).
 * - A non-blank code without an id on an *unassigned* property is rejected with
 *   400 `ses_hospedajes.integration_required`; on an already-assigned property it is a plain
 *   code edit that keeps the assignment.
 * - An absent or blank code needs no validation (blank clears code + assignment in the repo).
 */
private suspend fun requireValidSesAssignment(
    establishmentCode: String?,
    requestedIntegrationId: Uuid?,
    currentIntegrationId: Uuid?,
    accountId: Uuid,
    integrationsRepository: IntegrationsRepository,
) {
    val code = establishmentCode?.trim()
    if (requestedIntegrationId != null && code.isNullOrEmpty()) {
        throw BadParamRequestException(
            Errors.ERROR_SES_HOSPEDAJES_INTEGRATION_WITHOUT_CODE,
            code = Errors.CODE_SES_HOSPEDAJES_INTEGRATION_WITHOUT_CODE,
        )
    }
    if (code.isNullOrEmpty()) {
        return
    }
    if (requestedIntegrationId != null) {
        integrationsRepository.getSesHospedajesIntegrationById(accountId, requestedIntegrationId)
            ?: throw NotFoundException(Errors.ERROR_INTEGRATION_NOT_FOUND)
        if (currentIntegrationId != null && currentIntegrationId != requestedIntegrationId) {
            throw ConflictException(
                message = Errors.ERROR_SES_HOSPEDAJES_PROPERTY_ALREADY_CONFIGURED,
                code = Errors.CODE_SES_HOSPEDAJES_PROPERTY_ALREADY_CONFIGURED,
            )
        }
    } else if (currentIntegrationId == null) {
        throw BadParamRequestException(
            Errors.ERROR_SES_HOSPEDAJES_INTEGRATION_REQUIRED,
            code = Errors.CODE_SES_HOSPEDAJES_INTEGRATION_REQUIRED,
        )
    }
}

/**
 * Rejects an SES establishment code that matches the arrendador code of any of the account's
 * SES Hospedajes integrations. The two codes identify different things (property vs landlord)
 * and mixing them up is a real user mistake SES would only surface much later, at submission time.
 */
private suspend fun requireEstablishmentCodeNotArrendador(
    establishmentCode: String?,
    accountId: Uuid,
    integrationsRepository: IntegrationsRepository,
) {
    val code = establishmentCode?.trim()
    if (code.isNullOrEmpty()) {
        return
    }
    val matchesArrendador =
        integrationsRepository
            .getSesArrendadorCodesByAccountId(accountId)
            .any { it.trim().equals(code, ignoreCase = true) }
    if (matchesArrendador) {
        throw BadParamRequestException(
            Errors.ERROR_PROPERTY_SES_CODE_MATCHES_ARRENDADOR,
            code = Errors.CODE_PROPERTY_SES_CODE_MATCHES_ARRENDADOR,
        )
    }
}
