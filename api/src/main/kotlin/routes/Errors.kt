package eu.kasify.routes

object Errors {
    // Access codes
    const val ERROR_ACCESS_CODES_ALREADY_EXIST_IN_BOOKING = "Access codes already exist for this booking."
    const val ERROR_BOOKING_ALREADY_COMPLETED = "Cannot create access codes for a completed booking"

    // Resource not found
    const val ERROR_PROPERTY_NOT_FOUND = "Property not found"
    const val ERROR_BOOKING_NOT_FOUND = "Booking not found"
    const val ERROR_SMARTLOCK_NOT_FOUND = "Smartlock not found"
    const val ERROR_INTEGRATION_NOT_FOUND = "Integration not found"

    // Invalid ID format
    const val ERROR_INVALID_PROPERTY_ID_FORMAT = "Property ID has an incorrect format"
    const val ERROR_INVALID_BOOKING_ID_FORMAT = "Booking ID has an incorrect format"
    const val ERROR_INVALID_SMARTLOCK_ID_FORMAT = "Smartlock ID has an incorrect format"

    // Integration configuration
    const val ERROR_SMARTLOCKS_NOT_CONFIGURED = "Smart locks not configured"
    const val ERROR_NUKI_NOT_CONFIGURED = "Nuki integration not found"
    const val ERROR_UNIFI_NOT_CONFIGURED = "Unifi integration not found"
    const val ERROR_UNIFI_ALREADY_CONFIGURED = "Unifi already configured"

    // Validation
    const val ERROR_GUEST_NAME_NOT_CONFIGURED = "Guest name not configured"

    // Conflicts
    const val ERROR_SMARTLOCK_ALREADY_ASSIGNED = "This smart lock is already assigned to a property"
    const val ERROR_BOOKING_DATES_CONFLICT = "A booking already exists for the specified dates"

    // Checkin — codes (returned to the frontend for translated error display)
    const val CODE_CHECKIN_CLOSED = "checkin.closed"
    const val CODE_CHECKIN_BOOKING_FULL = "checkin.booking_full"
    const val CODE_CHECKIN_DUPLICATE_DOCUMENT = "checkin.duplicate_document"
    const val CODE_CHECKIN_REGISTRATION_FAILED = "checkin.registration_failed"
    const val CODE_CHECKIN_RELATIONSHIP_REQUIRED = "checkin.relationship_required"

    // Checkin — messages
    const val ERROR_CHECKIN_CLOSED = "Guest registration is closed for this booking"
    const val ERROR_CHECKIN_BOOKING_FULL = "All guests for this booking are already registered"
    const val ERROR_CHECKIN_DUPLICATE_DOCUMENT = "A guest with this document number is already registered for this booking"
    const val ERROR_CHECKIN_REGISTRATION_FAILED = "Failed to register guest"
    const val ERROR_CHECKIN_RELATIONSHIP_REQUIRED =
        "Relationship to the accompanying minor is required for adult guests when the booking includes minors"

    // Guests (owner-side management)
    const val CODE_GUEST_NOT_FOUND = "guest.not_found"
    const val ERROR_GUEST_NOT_FOUND = "Guest not found"

    // Guest data submitted to SES Hospedajes must be retained for 3 years (Spanish
    // law compliance), so editing or deleting it is blocked once the booking has been
    // submitted to the registry.
    const val CODE_GUEST_SES_LOCKED = "guest.ses_locked"
    const val ERROR_GUEST_SES_LOCKED =
        "This guest's data has been submitted to SES Hospedajes and cannot be edited or deleted."

    // Property — SES establishment code (returned to the frontend for translated error display).
    // The establishment code identifies one property; the arrendador code identifies the landlord
    // account. Entering the arrendador code as an establishment code is a common mix-up that SES
    // would reject later, so it is blocked at assignment time.
    const val CODE_PROPERTY_SES_CODE_MATCHES_ARRENDADOR = "property.establishment_code_matches_arrendador"
    const val ERROR_PROPERTY_SES_CODE_MATCHES_ARRENDADOR =
        "The establishment code cannot be the same as the SES arrendador code"

    // Property — default cleaner (returned to the frontend for translated error display).
    // A new default cleaner must be an owner (account member) or a crew member of the property.
    const val CODE_PROPERTY_DEFAULT_CLEANER_NOT_IN_PROPERTY = "property.default_cleaner_not_in_property"
    const val ERROR_PROPERTY_DEFAULT_CLEANER_NOT_IN_PROPERTY =
        "The default cleaner must be an owner or crew member of this property"

    // SES Hospedajes — credential verification (returned to the frontend for translated error display)
    const val CODE_SES_HOSPEDAJES_INVALID_CREDENTIALS = "ses_hospedajes.invalid_credentials"
    const val CODE_SES_HOSPEDAJES_VERIFICATION_FAILED = "ses_hospedajes.verification_failed"
    const val ERROR_SES_HOSPEDAJES_INVALID_CREDENTIALS = "SES Hospedajes rejected the provided credentials"
    const val ERROR_SES_HOSPEDAJES_VERIFICATION_FAILED = "Could not verify SES Hospedajes credentials"

    // SES Hospedajes — property config (SES does not recognize the property's establishment code;
    // the property is flagged and its auto-submissions pause — see SesPropertyConfigGuard)
    const val CODE_SES_HOSPEDAJES_PROPERTY_CONFIG_ERROR = "ses_hospedajes.property_config_error"
    const val ERROR_SES_HOSPEDAJES_PROPERTY_CONFIG_ERROR =
        "SES Hospedajes does not recognize the property's establishment code. Fix it on the integration's settings page."

    // SES Hospedajes — property↔integration assignment (one integration per property; strict).
    // A property participates in SES only when explicitly assigned to an integration; moving it
    // is two-step (clear the establishment code on the old integration first, which unassigns).
    const val CODE_SES_HOSPEDAJES_PROPERTY_ALREADY_CONFIGURED = "ses_hospedajes.property_already_configured"
    const val ERROR_SES_HOSPEDAJES_PROPERTY_ALREADY_CONFIGURED =
        "This property is already configured on another SES Hospedajes integration. Clear its establishment code there first."
    const val CODE_SES_HOSPEDAJES_INTEGRATION_REQUIRED = "ses_hospedajes.integration_required"
    const val ERROR_SES_HOSPEDAJES_INTEGRATION_REQUIRED =
        "Setting a SES establishment code requires the SES integration it belongs to (ses_integration_id)"
    const val CODE_SES_HOSPEDAJES_INTEGRATION_WITHOUT_CODE = "ses_hospedajes.integration_without_code"
    const val ERROR_SES_HOSPEDAJES_INTEGRATION_WITHOUT_CODE =
        "ses_integration_id is only accepted together with a non-blank ses_establishment_code"

    // SES Hospedajes — manual-submit eligibility (returned to the frontend for translated error display)
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_INTEGRATION = "ses_hospedajes.not_eligible.no_integration"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_PROPERTY_NOT_LINKED = "ses_hospedajes.not_eligible.property_not_linked"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ESTABLISHMENT_CODE = "ses_hospedajes.not_eligible.no_establishment_code"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_CANCELLED = "ses_hospedajes.not_eligible.cancelled"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_ALREADY_SUBMITTED = "ses_hospedajes.not_eligible.already_submitted"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_TERMINAL_STATE = "ses_hospedajes.not_eligible.terminal_state"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_ALREADY_HAS_LOTE = "ses_hospedajes.not_eligible.already_has_lote"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CHECKIN_COMPLETED = "ses_hospedajes.not_eligible.no_checkin_completed"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_BEFORE_CHECKIN = "ses_hospedajes.not_eligible.before_checkin"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUESTS = "ses_hospedajes.not_eligible.no_guests"

    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_INTEGRATION = "No enabled SES Hospedajes integration for this account."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_PROPERTY_NOT_LINKED =
        "The property is not assigned to a SES Hospedajes integration. Configure it from the integration's settings page."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ESTABLISHMENT_CODE = "Property has no SES establishment code configured."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_CANCELLED = "Booking is cancelled."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_ALREADY_SUBMITTED = "Booking has already been submitted to SES Hospedajes successfully."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_ALREADY_HAS_LOTE = "Booking already has an SES batch id; clear it before resubmitting."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CHECKIN_COMPLETED = "Guest has not completed the digital check-in yet."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_BEFORE_CHECKIN = "Cannot submit to SES Hospedajes before the booking's check-in time."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUESTS = "No guests registered for this booking."

    // RH (Reserva de Hospedaje) is filed only for direct bookings, and only when the account opts in.
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_NOT_DIRECT_BOOKING = "ses_hospedajes.not_eligible.rh_not_direct_booking"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_DISABLED = "ses_hospedajes.not_eligible.rh_disabled"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUEST_NAME = "ses_hospedajes.not_eligible.no_guest_name"
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CONTACT = "ses_hospedajes.not_eligible.no_contact"
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_NOT_DIRECT_BOOKING =
        "Reserva de Hospedaje is only filed for direct bookings; other channels self-file it."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_RH_DISABLED =
        "Reserva de Hospedaje auto-filing is disabled for this account."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_GUEST_NAME =
        "Booking has no principal guest name to file the Reserva de Hospedaje."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_CONTACT =
        "Booking's principal guest has no phone or email; SES requires at least one for a Reserva de Hospedaje."

    // Anulación (cancel) eligibility.
    const val CODE_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ACCEPTED_ALTA = "ses_hospedajes.not_eligible.no_accepted_alta"
    const val CODE_SES_HOSPEDAJES_ALREADY_CANCELLED = "ses_hospedajes.already_cancelled"
    const val CODE_SES_HOSPEDAJES_COMMUNICATION_NOT_FOUND = "ses_hospedajes.communication_not_found"
    const val ERROR_SES_HOSPEDAJES_COMMUNICATION_NOT_FOUND = "SES communication not found."
    const val ERROR_SES_HOSPEDAJES_NOT_ELIGIBLE_NO_ACCEPTED_ALTA =
        "There is no accepted SES communication to cancel for this booking."
    const val ERROR_SES_HOSPEDAJES_ALREADY_CANCELLED =
        "A cancellation for this SES communication is already in progress or completed."

    // Marketing (guest email opt-in / unsubscribe)
    const val CODE_MARKETING_UNSUBSCRIBE_NOT_FOUND = "marketing.unsubscribe_not_found"
    const val ERROR_MARKETING_UNSUBSCRIBE_NOT_FOUND = "No marketing subscription matches this unsubscribe link."

    // Telegram linking (returned to the frontend for translated error display)
    const val CODE_TELEGRAM_DISABLED = "telegram.disabled"
    const val CODE_TELEGRAM_ALREADY_LINKED = "telegram.already_linked"
    const val CODE_TELEGRAM_NOT_LINKED = "telegram.not_linked"
    const val ERROR_TELEGRAM_DISABLED = "Telegram notifications are not enabled."
    const val ERROR_TELEGRAM_ALREADY_LINKED = "Your Telegram account is already linked."
    const val ERROR_TELEGRAM_NOT_LINKED = "No Telegram account is linked."

    // Tasks (returned to the frontend for translated error display)
    // A task may only link a booking of its own property; an unknown booking id and another
    // property's booking get the same code so the response does not reveal which ids exist.
    const val CODE_TASK_BOOKING_NOT_IN_PROPERTY = "task.booking_not_in_property"
    const val ERROR_TASK_BOOKING_NOT_IN_PROPERTY = "The booking does not belong to this property"

    // A task may only be assigned to an owner (account member) or a crew member of its property.
    const val CODE_TASK_ASSIGNEE_NOT_IN_PROPERTY = "task.assignee_not_in_property"
    const val ERROR_TASK_ASSIGNEE_NOT_IN_PROPERTY =
        "The assignee must be an owner or crew member of this property"
}
