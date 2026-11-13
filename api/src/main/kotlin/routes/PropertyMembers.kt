package eu.kasify.routes
import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.PropertyInvitationsRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.InvitationRedemption
import eu.kasify.model.api.PropertyInvitation
import eu.kasify.model.api.requests.CreatePropertyInvitationRequest
import eu.kasify.model.api.requests.UpdatePropertyMemberRequest
import eu.kasify.model.api.responses.AcceptInvitationResponse
import eu.kasify.model.api.responses.PropertyInvitationsResponse
import eu.kasify.model.api.responses.PropertyMembersResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ConflictException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getPathProperty
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AuthorizationService
import eu.kasify.services.notifications.NotificationService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Validates the optional invitation email. Accepts exactly the same strings as the former
 * `^[^\s@]+@[^\s@]+\.[^\s@]+$`, but is written so the engine can never backtrack: every
 * quantifier is possessive and the domain is split deterministically at its first dot after
 * the first character. The former pattern let the greedy domain run also match `.`, so an
 * input like `a@` + "." x N + `@` cost O(N^2) before failing (CWE-1333).
 */
internal val INVITATION_EMAIL_REGEX = Regex("^[^\\s@]++@[^\\s@][^\\s@.]*+\\.[^\\s@]++$")

internal fun isValidInvitationEmail(email: String): Boolean = INVITATION_EMAIL_REGEX.matches(email)

@OptIn(ExperimentalKtorApi::class)
fun Application.configurePropertyMembersRouting() {
    val propertiesRepository by inject<PropertiesRepository>()
    val propertyInvitationsRepository by inject<PropertyInvitationsRepository>()
    val propertyAccessRepository by inject<PropertyAccessRepository>()
    val usersRepository by inject<UsersRepository>()
    val authorizationService by inject<AuthorizationService>()
    val notificationService by inject<NotificationService>()

    versionedRouting {
        authenticatedAndRateLimited {
            // Create an invitation for a property
            post("/properties/{id}/invitations") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                authorizationService.requireOwner(user, property)

                val request = call.receive<CreatePropertyInvitationRequest>()

                if (request.email != null && !isValidInvitationEmail(request.email)) {
                    throw BadParamRequestException(
                        "Invalid email address",
                        code = "invitation.invalid_email",
                    )
                }
                val email = request.email

                val invitation =
                    propertyInvitationsRepository.create(
                        propertyId = property.id,
                        email = email,
                        maxUses = request.maxUses,
                        expiresAt = LocalDateTime.now().plusDays(7),
                        accessLevel = request.accessLevel,
                        locale = request.locale,
                    )

                if (!email.isNullOrBlank()) {
                    val inviter = usersRepository.getUserById(user.userId)
                    if (inviter != null) {
                        notificationService.enqueueInvitation(invitation, property, inviter)
                    }
                }

                call.respond(HttpStatusCode.Created, PropertyInvitation.from(invitation))
            }.describe {
                tag("PropertyMembers")
                summary = "Create crew invitation"
                description = "Generates an invitation token (optionally bound to a specific email) that grants the invitee crew access with the chosen level when accepted. When an email is provided, the invitee receives an invitation email rendered in the request's `locale`."
                parameters {
                    path("id") { description = "Property id" }
                }
                requestBody {
                    description = "Invitation details (email optional, max uses, access level)"
                    required = true
                    schema = jsonSchema<CreatePropertyInvitationRequest>()
                }
                responses {
                    HttpStatusCode.Created {
                        description = "The created invitation. Expires seven days after creation."
                        schema = jsonSchema<PropertyInvitation>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invalid email format. Returns `invitation.invalid_email`"
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            // List all invitations for a property
            get("/properties/{id}/invitations") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                authorizationService.requireOwner(user, property)

                val invitations = propertyInvitationsRepository.findByPropertyId(property.id)
                call.respond(PropertyInvitationsResponse(invitations.map { PropertyInvitation.from(it) }))
            }.describe {
                tag("PropertyMembers")
                summary = "List property invitations"
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Invitations for the property, including expired and exhausted ones"
                        schema = jsonSchema<PropertyInvitationsResponse>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            // Revoke an invitation
            delete("/invitations/{id}") {
                val user = call.getKasifyUser()
                val invitationId =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing invitation ID")

                // Get the invitation to check property ownership
                val invitation = propertyInvitationsRepository.findById(invitationId)

                if (invitation == null) {
                    throw NotFoundException("Invitation not found")
                }

                val property =
                    propertiesRepository.getProperty(invitation.propertyId)
                        ?: throw NotFoundException("Property not found")

                authorizationService.requireOwner(user, property)

                propertyInvitationsRepository.delete(invitationId)
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("PropertyMembers")
                summary = "Revoke invitation"
                parameters {
                    path("id") { description = "Invitation id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Invitation deleted" }
                    HttpStatusCode.Forbidden { description = "Caller is not the owner of the invitation's property" }
                    HttpStatusCode.NotFound { description = "Invitation or property not found" }
                }
            }

            // Accept an invitation (requires authentication)
            post("/invitations/{token}/accept") {
                val user = call.getKasifyUser()
                val token =
                    call.parameters["token"]
                        ?: throw MissingRequestParameterException("Missing invitation token")

                val invitation =
                    propertyInvitationsRepository.findByToken(token)
                        ?: throw NotFoundException(
                            "Invitation not found or expired",
                            code = "invitation.not_found",
                        )

                if (!invitation.isValid) {
                    throw BadParamRequestException(
                        "Invitation is expired or has reached its usage limit",
                        code = "invitation.expired",
                    )
                }

                val property =
                    propertiesRepository.getProperty(invitation.propertyId)
                        ?: throw NotFoundException("Property not found")

                // Check if user is already account owner
                if (user.accountId == property.accountId) {
                    throw ConflictException("You are already an owner of this property", code = "invitation.already_owner")
                }

                // Check if user already has member access
                val existingAccess = propertyAccessRepository.hasAccess(user.userId, property.id)
                if (existingAccess != null) {
                    throw ConflictException("You already have access to this property", code = "invitation.already_member")
                }

                // Grant member access and consume one use atomically: the invitation is re-validated
                // under a row lock, so concurrent accepts can never exceed its usage limit.
                when (propertyInvitationsRepository.redeem(token, user.userId)) {
                    InvitationRedemption.Redeemed -> Unit
                    InvitationRedemption.NotFound ->
                        throw NotFoundException(
                            "Invitation not found or expired",
                            code = "invitation.not_found",
                        )
                    InvitationRedemption.Invalid ->
                        throw BadParamRequestException(
                            "Invitation is expired or has reached its usage limit",
                            code = "invitation.expired",
                        )
                }

                // Notify the property owners that a new member joined
                val joinedUser = usersRepository.getUserById(user.userId)
                if (joinedUser != null) {
                    val owners = usersRepository.getUsersByAccountId(property.accountId)
                    notificationService.enqueueMemberJoined(
                        joinedUser,
                        property,
                        invitation.accessLevel,
                        invitation.locale,
                        owners,
                    )
                }

                call.respond(
                    AcceptInvitationResponse(
                        propertyId = property.id,
                        propertyName = property.name,
                    ),
                )
            }.describe {
                tag("PropertyMembers")
                summary = "Accept crew invitation"
                description = "Grants the authenticated caller crew access to the invitation's property at the invitation's access level and increments the invitation's usage counter."
                parameters {
                    path("token") { description = "Invitation token" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Invitation accepted; the caller now has access to the property"
                        schema = jsonSchema<AcceptInvitationResponse>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Invitation has expired or hit its usage limit. Returns `invitation.expired`"
                    }
                    HttpStatusCode.NotFound {
                        description = "No invitation with this token. Returns `invitation.not_found`"
                    }
                    HttpStatusCode.Conflict {
                        description = "Caller is already an owner (`invitation.already_owner`) or already has crew access (`invitation.already_member`)"
                    }
                }
            }

            // List property members for a property
            get("/properties/{id}/crew") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                authorizationService.requireOwner(user, property)

                val crew = propertyAccessRepository.getPropertyMembersByPropertyId(property.id)
                val owners = usersRepository.getUsersByAccountId(property.accountId)
                val members = (owners + crew).distinctBy { it.id }
                call.respond(PropertyMembersResponse(members, property.defaultCleanerId))
            }.describe {
                tag("PropertyMembers")
                summary = "List property members (owners and crew)"
                parameters {
                    path("id") { description = "Property id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Property members (owners and crew) with their access levels and services, plus the property's default cleaner id"
                        schema = jsonSchema<PropertyMembersResponse>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            // Remove property member from a property
            delete("/properties/{id}/crew/{userId}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                authorizationService.requireOwner(user, property)

                val memberUserId =
                    call.parameters["userId"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing user ID")

                val revoked = propertyAccessRepository.revoke(memberUserId, property.id)
                if (!revoked) {
                    throw NotFoundException("Property member not found for this property")
                }

                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("PropertyMembers")
                summary = "Remove crew member"
                parameters {
                    path("id") { description = "Property id" }
                    path("userId") { description = "User id of the crew member to remove" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Crew member removed" }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found, or the user has no crew access to this property" }
                }
            }

            // Update property member access level
            patch("/properties/{id}/crew/{userId}") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)

                authorizationService.requireOwner(user, property)

                val memberUserId =
                    call.parameters["userId"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing user ID")

                val request = call.receive<UpdatePropertyMemberRequest>()

                val updated = propertyAccessRepository.updateAccessLevel(memberUserId, property.id, request.accessLevel)
                if (!updated) {
                    throw NotFoundException("Property member not found for this property")
                }

                call.respond(HttpStatusCode.OK)
            }.describe {
                tag("PropertyMembers")
                summary = "Update crew member access"
                parameters {
                    path("id") { description = "Property id" }
                    path("userId") { description = "User id of the crew member to update" }
                }
                requestBody {
                    description = "New access level for the crew member"
                    required = true
                    schema = jsonSchema<UpdatePropertyMemberRequest>()
                }
                responses {
                    HttpStatusCode.OK { description = "Access level updated" }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Property not found, or the user has no crew access to this property" }
                }
            }
        }
    }
}
