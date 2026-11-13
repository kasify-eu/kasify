package eu.kasify.model.api.requests

import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import eu.kasify.serialization.JavaLocalDateSerializer
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
data class CreateTaskRequest(
    @SerialName("property_id")
    val propertyId: Uuid,
    @SerialName("booking_id")
    val bookingId: Uuid? = null,
    @SerialName("task_type")
    val taskType: PropertyService,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val from: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val to: LocalDateTime,
    @SerialName("assigned_to")
    val assignedTo: Uuid? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateTaskRequest(
    @SerialName("assigned_to")
    val assignedTo: Uuid? = null,
    val notes: String? = null,
    val status: TaskStatus? = null,
    @SerialName("job_report")
    val jobReport: String? = null,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val from: LocalDateTime? = null,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val to: LocalDateTime? = null,
)

@Serializable
data class TasksFiltersParams(
    @SerialName("property_id")
    val propertyId: Uuid? = null,
    @SerialName("assigned_to")
    val assignedTo: Uuid? = null,
    val status: TaskStatus? = null,
    @SerialName("task_type")
    val taskType: PropertyService? = null,
    @Serializable(with = JavaLocalDateSerializer::class)
    @SerialName("scheduled_from")
    val scheduledFrom: LocalDate? = null,
    @Serializable(with = JavaLocalDateSerializer::class)
    @SerialName("scheduled_to")
    val scheduledTo: LocalDate? = null,
    @SerialName("include_cancelled")
    val includeCancelled: Boolean = false,
)
