package eu.kasify.model

/**
 * The kind of event a notification represents. [dbValue] is stored in `notifications.event_type` and
 * doubles as the template key under `resources/email/` and `resources/telegram/`. Add a new entry
 * here (plus the matching templates) for every new notifiable event.
 */
enum class NotificationType(
    val dbValue: String,
) {
    Invitation("invitation"),
    MemberJoined("member_joined"),
    CheckinCompleted("checkin_completed"),
    SesCredentialsError("ses_credentials_error"),
    SesPropertyConfigError("ses_property_config_error"),
    SesBookingRejected("ses_booking_rejected"),
    ;

    companion object {
        fun fromDb(value: String): NotificationType =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown notification event type: $value")
    }
}
