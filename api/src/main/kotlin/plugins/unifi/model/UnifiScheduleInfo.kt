package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnifiScheduleInfo(
    @SerialName("unique_id")
    val uniqueId: String,
    val name: String,
    val type: String? = null,
    val status: Int,
    @SerialName("is_default")
    val isDefault: Boolean,
    @SerialName("is_private")
    val isPrivate: Boolean,
    @SerialName("schedule_type")
    val scheduleType: String,
    @SerialName("holiday_group_id")
    val holidayGroupId: String,
    @SerialName("holiday_timezone_id")
    val holidayTimezoneId: String,
    @SerialName("user_timezone_id")
    val userTimezoneId: String,
    @SerialName("create_time")
    val createTime: String,
    @SerialName("update_time")
    val updateTime: String,
    @SerialName("week_schedule")
    val weekSchedule: UnifiWeekScheduleDetail? = null,
    @SerialName("holiday_schedule")
    val holidaySchedule: UnifiHolidaySchedule? = null,
)

@Serializable
data class UnifiWeekScheduleDetail(
    val id: String,
    @SerialName("unique_id")
    val uniqueId: String,
    val name: String,
    val description: String? = null,
    @SerialName("week_schedule")
    val weekSchedule: Map<String, List<UnifiTimeSlotDetail>>? = null,
    val system: Boolean,
    @SerialName("is_default")
    val isDefault: Boolean,
)

@Serializable
data class UnifiTimeSlotDetail(
    @SerialName("start_time")
    val startTime: String,
    @SerialName("end_time")
    val endTime: String,
)

@Serializable
data class UnifiHolidaySchedule(
    @SerialName("holiday_group")
    val holidayGroup: UnifiHolidayGroup? = null,
    @SerialName("holiday_timezone")
    val holidayTimezone: UnifiHolidayTimezone? = null,
)

@Serializable
data class UnifiHolidayGroup(
    @SerialName("unique_id")
    val uniqueId: String,
    val name: String,
    val description: String? = null,
    val holidays: List<String> = emptyList(),
    @SerialName("template_name")
    val templateName: String? = null,
    @SerialName("create_time")
    val createTime: String,
    @SerialName("is_default")
    val isDefault: Boolean,
    @SerialName("is_private")
    val isPrivate: Boolean,
)

@Serializable
data class UnifiHolidayTimezone(
    val id: String,
    @SerialName("unique_id")
    val uniqueId: String,
    val name: String,
    val description: String? = null,
    val system: Boolean,
    @SerialName("is_default")
    val isDefault: Boolean,
    @SerialName("day_schedule")
    val daySchedule: List<UnifiTimeSlotDetail> = emptyList(),
)
