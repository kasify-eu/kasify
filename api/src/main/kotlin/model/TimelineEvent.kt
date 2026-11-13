package eu.kasify.model

import eu.kasify.db.model.EventType
import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlin.uuid.Uuid

class TimelineEvent(
    val type: EventType,
    val propertyId: Uuid,
    // Account owning the property; used to resolve the caller's access per property.
    val accountId: Uuid,
    val propertyName: String?,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val booking: Booking?,
    val taskId: Uuid?,
    val taskType: PropertyService?,
    val taskStatus: TaskStatus?,
    val assignedToName: String?,
    val taskNotes: String?,
    val scheduledStartTime: LocalTime?,
    val scheduledEndTime: LocalTime?,
)
