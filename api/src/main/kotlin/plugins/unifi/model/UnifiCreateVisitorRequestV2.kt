package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Request-specific group info (uses simplified resource items without resourceName)
@Serializable
data class UnifiGroupInfoRequest(
    @SerialName("group_name")
    val groupName: String,
    @SerialName("group_type")
    val groupType: String = "visitor",
    @SerialName("group_resources")
    val groupResources: List<UnifiGroupResourceItemRequest>,
)

// Request-specific group resource item (floor level)
@Serializable
data class UnifiGroupResourceItemRequest(
    @SerialName("resource_id")
    val resourceId: String,
    @SerialName("resource_type")
    val resourceType: String,
    val resources: List<UnifiResourceItemRequest> = emptyList(),
)

// Request-specific resource item (door level)
@Serializable
data class UnifiResourceItemRequest(
    @SerialName("resource_id")
    val resourceId: String,
    @SerialName("resource_type")
    val resourceType: String,
)

// Resource wrapper for simplified visitor creation (uses request-specific group info)
@Serializable
data class UnifiResourceRequest(
    @SerialName("resource_value")
    val resourceValue: String = "",
    @SerialName("resource_type")
    val resourceType: String = "access_group",
    @SerialName("unique_id")
    val uniqueId: String = "",
    @SerialName("group_info")
    val groupInfo: UnifiGroupInfoRequest,
)

// Inline schedule with week schedule (for visitor creation)
@Serializable
data class UnifiInlineSchedule(
    val name: String,
    @SerialName("schedule_type")
    val scheduleType: String = "access",
    @SerialName("is_private")
    val isPrivate: Boolean = true,
    @SerialName("holiday_timezone")
    val holidayTimezone: UnifiInlineHolidayTimezone,
    @SerialName("user_timezone")
    val userTimezone: UnifiInlineUserTimezone,
    @SerialName("holiday_group_id")
    val holidayGroupId: String = "",
)

@Serializable
data class UnifiInlineHolidayTimezone(
    val name: String,
    @SerialName("day_schedule")
    val daySchedule: List<String> = emptyList(),
)

@Serializable
data class UnifiInlineUserTimezone(
    val name: String,
    @SerialName("week_schedule")
    val weekSchedule: Map<String, List<UnifiInlineTimeSlot>>,
)

@Serializable
data class UnifiInlineTimeSlot(
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
)

// Request model for simplified visitor creation with inline schedule
@Serializable
data class UnifiCreateVisitorSimplifiedRequest(
    @SerialName("assign_qr_code")
    val assignQrCode: Boolean = false,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    val remarks: String = "",
    val email: String = "",
    @SerialName("visitor_company")
    val visitorCompany: String = "",
    @SerialName("start_time")
    val startTime: Long,
    @SerialName("end_time")
    val endTime: Long,
    @SerialName("visit_reason")
    val visitReason: String = "Others",
    @SerialName("inform_user_ids")
    val informUserIds: List<String> = emptyList(),
    @SerialName("nfc_cards")
    val nfcCards: List<String> = emptyList(),
    @SerialName("pin_code")
    val pinCode: String? = null,
    val resource: List<UnifiResourceRequest>,
    @SerialName("license_plates")
    val licensePlates: List<String> = emptyList(),
    @SerialName("require_check_in")
    val requireCheckIn: Boolean = false,
    val schedule: UnifiInlineSchedule,
)
