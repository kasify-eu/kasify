package eu.kasify.services.notifications

import eu.kasify.config.EmailConfig
import eu.kasify.db.NewNotification
import eu.kasify.db.NotificationsRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.model.Booking
import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.NotificationType
import eu.kasify.model.Property
import eu.kasify.model.PropertyInvitation
import eu.kasify.model.SesCommunicationType
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.model.User
import eu.kasify.model.api.PropertyMember
import eu.kasify.model.email.Locale
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

/**
 * Producer side of the notifications outbox. Builds the per-event payload and writes one `pending`
 * row per resolved channel, then returns — delivery happens asynchronously in
 * [eu.kasify.jobs.NotificationDispatchJob]. Enqueueing is **best-effort**: any failure is logged
 * (and surfaced to Sentry via the logger) but never propagated, so a notification problem can never
 * roll back the business operation that triggered it — the same guarantee the old inline email had,
 * now with durability and automatic retries on top.
 */
open class NotificationService(
    private val notificationsRepository: NotificationsRepository,
    private val channelResolver: ChannelResolver,
    private val config: EmailConfig,
) {
    private val logger = KotlinLogging.logger {}

    /** Enqueue to a registered user across all their enabled channels (email always; Telegram if linked). */
    open suspend fun enqueueToUser(
        userId: Uuid,
        type: NotificationType,
        payload: NotificationPayload,
    ) {
        bestEffort(type) {
            val channels = channelResolver.channelsForUser(userId)
            insert(userId = userId, emailOverride = null, type = type, channels = channels, payload = payload)
        }
    }

    /** Enqueue to a raw email address (recipient has no user account yet, e.g. an invitee). */
    open suspend fun enqueueToEmail(
        email: String,
        type: NotificationType,
        payload: NotificationPayload,
    ) {
        bestEffort(type) {
            insert(
                userId = null,
                emailOverride = email,
                type = type,
                channels = channelResolver.channelsForEmailOnly(),
                payload = payload,
            )
        }
    }

    /** Crew invitation email to the (not-yet-registered) invitee. */
    open suspend fun enqueueInvitation(
        invitation: PropertyInvitation,
        property: Property,
        inviter: User,
    ) {
        val recipient = invitation.email
        if (recipient.isNullOrBlank()) return

        val dashboardUrl = config.dashboardBaseUrl.trimEnd('/')
        val payload =
            NotificationPayload(
                templateKey = NotificationType.Invitation.dbValue,
                locale = invitation.locale,
                placeholders =
                    mapOf(
                        "inviter_name" to userDisplayName(inviter),
                        "inviter_email" to inviter.email,
                        "property_name" to property.name,
                        "access_level_label" to accessLevelLabel(invitation.accessLevel, invitation.locale),
                        "accept_url" to "$dashboardUrl/properties?invitation=${invitation.token}",
                        "dashboard_url" to dashboardUrl,
                        "invitation_code" to invitation.token,
                    ),
                tags = mapOf("type" to NotificationType.Invitation.dbValue, "property_id" to property.id.toString()),
            )
        enqueueToEmail(recipient, NotificationType.Invitation, payload)
    }

    /** Notifies each property owner that [member] joined. One notification (per channel) per owner. */
    open suspend fun enqueueMemberJoined(
        member: User,
        property: Property,
        accessLevel: PropertyAccessLevel,
        locale: Locale,
        owners: List<PropertyMember>,
    ) {
        val dashboardUrl = config.dashboardBaseUrl.trimEnd('/')
        val payload =
            NotificationPayload(
                templateKey = NotificationType.MemberJoined.dbValue,
                locale = locale,
                placeholders =
                    mapOf(
                        "member_name" to userDisplayName(member),
                        "member_email" to member.email,
                        "property_name" to property.name,
                        "access_level_label" to accessLevelLabel(accessLevel, locale),
                        "dashboard_url" to dashboardUrl,
                    ),
                tags = mapOf("type" to NotificationType.MemberJoined.dbValue, "property_id" to property.id.toString()),
            )
        owners.distinctBy { it.id }.forEach { owner ->
            enqueueToUser(owner.id, NotificationType.MemberJoined, payload)
        }
    }

    /**
     * Notifies each [recipient] (property owners and managers) that a booking's guest check-in has
     * been completed. One payload is built per recipient in that recipient's own [User.locale].
     */
    open suspend fun enqueueCheckinCompleted(
        booking: Booking,
        property: Property,
        recipients: List<User>,
    ) {
        val dashboardUrl = config.dashboardBaseUrl.trimEnd('/')
        val guestName = "${booking.guestFirstName.orEmpty()} ${booking.guestLastName.orEmpty()}".trim()
        val guestsCount =
            (booking.adultsCount ?: 0) + (booking.childrenCount ?: 0) + (booking.infantsCount ?: 0)

        // The placeholders and tags are identical for every recipient — only the locale varies —
        // so build them once and reuse them across the per-recipient payloads.
        val placeholders =
            mapOf(
                "property_name" to property.name,
                "guest_name" to guestName,
                "checkin_from" to booking.from.toString(),
                "checkin_to" to booking.to.toString(),
                "guests_count" to guestsCount.toString(),
                "dashboard_url" to dashboardUrl,
            )
        val tags =
            mapOf(
                "type" to NotificationType.CheckinCompleted.dbValue,
                "property_id" to property.id.toString(),
                "booking_id" to booking.id.toString(),
            )

        recipients.distinctBy { it.id }.forEach { recipient ->
            enqueueToUser(
                recipient.id,
                NotificationType.CheckinCompleted,
                NotificationPayload(
                    templateKey = NotificationType.CheckinCompleted.dbValue,
                    locale = recipient.locale,
                    placeholders = placeholders,
                    tags = tags,
                ),
            )
        }
    }

    /**
     * Notify the account owners that SES Hospedajes rejected a property's establishment code and
     * that this property's guest-registry submissions are paused until it is fixed. Fired once per
     * outage (the caller only enqueues on the null→error transition of the property flag).
     */
    open suspend fun enqueueSesPropertyConfigError(
        property: Property,
        errorMessage: String?,
        recipients: List<User>,
    ) {
        // Deep-link to the assigned integration's details page — that page hosts the per-property
        // establishment-code cards where the code is corrected and re-saved (which clears the pause).
        val settingsUrl =
            property.sesIntegrationId?.let { "${config.dashboardBaseUrl.trimEnd('/')}/integrations/ses-hospedajes/$it" }
                ?: "${config.dashboardBaseUrl.trimEnd('/')}/integrations"
        val placeholders =
            mapOf(
                "property_name" to property.name,
                "establishment_code" to (property.sesEstablishmentCode ?: "—"),
                "error_message" to (errorMessage ?: "Establishment code rejected by SES"),
                "settings_url" to settingsUrl,
            )
        val tags =
            mapOf(
                "type" to NotificationType.SesPropertyConfigError.dbValue,
                "account_id" to property.accountId.toString(),
                "property_id" to property.id.toString(),
            )

        recipients.distinctBy { it.id }.forEach { recipient ->
            enqueueToUser(
                recipient.id,
                NotificationType.SesPropertyConfigError,
                NotificationPayload(
                    templateKey = NotificationType.SesPropertyConfigError.dbValue,
                    locale = recipient.locale,
                    placeholders = placeholders,
                    tags = tags,
                ),
            )
        }
    }

    /**
     * Notify the account owners that SES Hospedajes rejected one booking's guest-registry
     * submission because of the communicated data (comm row `rejected` — e.g. a Layer-1 validation
     * código or an estado-6 per-communication error). Unlike the credential/property-config pauses
     * this is a per-event notification, not a per-outage one: each rejection is a distinct
     * actionable failure, and the rejected-alta retry gate already bounds the frequency to one
     * rejection per data change.
     */
    open suspend fun enqueueSesBookingRejected(
        booking: Booking,
        property: Property,
        communicationType: SesCommunicationType,
        errorMessage: String?,
        recipients: List<User>,
    ) {
        // Deep-link to the bookings list with the drawer opened on the affected booking — that's
        // where the owner reviews and corrects the booking/guest data (which reopens the retry gate).
        val bookingUrl = "${config.dashboardBaseUrl.trimEnd('/')}/bookings?bookingId=${booking.id}"
        val guestName =
            "${booking.guestFirstName.orEmpty()} ${booking.guestLastName.orEmpty()}".trim().ifBlank { "—" }
        val tags =
            mapOf(
                "type" to NotificationType.SesBookingRejected.dbValue,
                "account_id" to property.accountId.toString(),
                "property_id" to property.id.toString(),
                "booking_id" to booking.id.toString(),
            )

        recipients.distinctBy { it.id }.forEach { recipient ->
            val placeholders =
                mapOf(
                    "property_name" to property.name,
                    "guest_name" to guestName,
                    "booking_from" to booking.from.toString(),
                    "booking_to" to booking.to.toString(),
                    "communication_type_label" to communicationTypeLabel(communicationType, recipient.locale),
                    "error_message" to (errorMessage ?: "SES rejected the submitted data"),
                    "booking_url" to bookingUrl,
                )
            enqueueToUser(
                recipient.id,
                NotificationType.SesBookingRejected,
                NotificationPayload(
                    templateKey = NotificationType.SesBookingRejected.dbValue,
                    locale = recipient.locale,
                    placeholders = placeholders,
                    tags = tags,
                ),
            )
        }
    }

    /**
     * Notify the account owners that SES Hospedajes rejected the integration's web-service
     * credentials and that guest-registry submissions are paused until they are fixed. Fired once
     * per outage (the caller only enqueues on the null→error transition of the integration flag).
     */
    open suspend fun enqueueSesCredentialsError(
        integration: SesHospedajesIntegration,
        errorCodigo: Int,
        errorDescripcion: String?,
        recipients: List<User>,
    ) {
        // Deep-link to the integration's details page (error banner + credentials-update form).
        // The bare /integrations/ses-hospedajes path is the *creation* form — linking there would
        // invite the owner to create a second integration instead of fixing the flagged one.
        val settingsUrl =
            integration.id?.let { "${config.dashboardBaseUrl.trimEnd('/')}/integrations/ses-hospedajes/$it" }
                ?: "${config.dashboardBaseUrl.trimEnd('/')}/integrations"
        val placeholders =
            mapOf(
                "error_code" to errorCodigo.toString(),
                "error_message" to (errorDescripcion ?: "Credentials rejected"),
                "ses_username" to integration.sesUsername,
                "arrendador_code" to integration.arrendadorCode,
                "settings_url" to settingsUrl,
            )
        val tags =
            mapOf(
                "type" to NotificationType.SesCredentialsError.dbValue,
                "account_id" to integration.accountId.toString(),
                "ses_error_code" to errorCodigo.toString(),
            )

        recipients.distinctBy { it.id }.forEach { recipient ->
            enqueueToUser(
                recipient.id,
                NotificationType.SesCredentialsError,
                NotificationPayload(
                    templateKey = NotificationType.SesCredentialsError.dbValue,
                    locale = recipient.locale,
                    placeholders = placeholders,
                    tags = tags,
                ),
            )
        }
    }

    private suspend fun insert(
        userId: Uuid?,
        emailOverride: String?,
        type: NotificationType,
        channels: List<NotificationChannelType>,
        payload: NotificationPayload,
    ) {
        if (channels.isEmpty()) return
        val groupId = Uuid.random()
        val rows =
            channels.map { channel ->
                NewNotification(
                    groupId = groupId,
                    userId = userId,
                    emailOverride = emailOverride,
                    eventType = type,
                    channel = channel,
                    payload = payload,
                )
            }
        notificationsRepository.enqueue(rows)
        logger.debug { "Enqueued ${rows.size} notification(s) type=${type.dbValue} channels=$channels" }
    }

    private suspend fun bestEffort(
        type: NotificationType,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (t: Throwable) {
            logger.error(t) { "Failed to enqueue notification type=${type.dbValue}" }
        }
    }

    private fun userDisplayName(user: User): String {
        val fullName = "${user.name} ${user.lastName}".trim()
        return fullName.ifBlank { user.email }
    }

    private fun communicationTypeLabel(
        type: SesCommunicationType,
        locale: Locale,
    ): String =
        when (locale) {
            Locale.ES ->
                when (type) {
                    SesCommunicationType.PV -> "Parte de viajeros (PV)"
                    SesCommunicationType.RH -> "Reserva de hospedaje (RH)"
                }
            Locale.EN ->
                when (type) {
                    SesCommunicationType.PV -> "Traveler report (PV)"
                    SesCommunicationType.RH -> "Booking notice (RH)"
                }
        }

    private fun accessLevelLabel(
        level: PropertyAccessLevel,
        locale: Locale,
    ): String =
        when (locale) {
            Locale.ES ->
                when (level) {
                    PropertyAccessLevel.Owner -> "propietario"
                    PropertyAccessLevel.Manager -> "encargado"
                    PropertyAccessLevel.Staff -> "personal"
                    PropertyAccessLevel.Contractor -> "colaborador"
                }
            Locale.EN ->
                when (level) {
                    PropertyAccessLevel.Owner -> "owner"
                    PropertyAccessLevel.Manager -> "manager"
                    PropertyAccessLevel.Staff -> "staff"
                    PropertyAccessLevel.Contractor -> "contractor"
                }
        }
}
