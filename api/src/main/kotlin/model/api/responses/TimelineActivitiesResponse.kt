package eu.kasify.model.api.responses

import eu.kasify.model.api.Booking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimelineActivitiesResponse(
    val timeline: List<TimelineDay>,
)

@Serializable
data class TimelineDay(
    val date: String,
    @SerialName("date_offset")
    val dateOffset: Int,
    val activities: List<TimelineActivity>,
)

@Serializable
data class TimelineActivity(
    val id: String,
    val type: ActivityType,
    val date: String? = null,
    val time: String? = null,
    @SerialName("end_time")
    val endTime: String? = null,
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("property_id")
    val propertyId: String,
    val booking: Booking? = null,
    val task: TimelineTask? = null,
)

@Serializable
data class ActivitiesResponse(
    val activities: List<TimelineActivity>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse

@Serializable
data class TimelineTask(
    val id: String,
    @SerialName("property_name")
    val propertyName: String?,
    val status: String,
    @SerialName("assigned_to_name")
    val assignedToName: String?,
    val notes: String? = null,
)

@Serializable
@Suppress("EnumEntryName", "ktlint:standard:enum-entry-name-case")
enum class ActivityType {
    booking,
    check_in,
    check_out,
    current_guest,
    cleaning,
    maintenance,
}
