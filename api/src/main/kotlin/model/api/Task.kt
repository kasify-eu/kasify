package eu.kasify.model.api

import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import eu.kasify.serialization.JavaLocalDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.uuid.Uuid

@Serializable
class Task(
    val id: Uuid,
    val property: Property,
    @VisibleTo(Visibility.OWNER)
    @SerialName("booking_id")
    val bookingId: Uuid?,
    @SerialName("task_type")
    val taskType: PropertyService,
    val status: TaskStatus,
    @SerialName("assigned_to")
    val assignedTo: Uuid,
    @SerialName("assigned_to_name")
    val assignedToName: String,
    val notes: String?,
    @SerialName("job_report")
    val jobReport: String?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val from: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    val to: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @VisibleTo(Visibility.OWNER)
    @SerialName("created_at")
    val createdAt: LocalDateTime,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("started_at")
    val startedAt: LocalDateTime?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @SerialName("completed_at")
    val completedAt: LocalDateTime?,
    @Serializable(with = JavaLocalDateTimeSerializer::class)
    @VisibleTo(Visibility.OWNER)
    @SerialName("cancelled_at")
    val cancelledAt: LocalDateTime?,
    @SerialName("next_booking")
    val nextBooking: Booking? = null,
    val access: String? = null,
    @SerialName("public_token")
    val publicToken: String? = null,
) {
    companion object {
        fun from(
            task: eu.kasify.model.Task,
            nextBooking: Booking? = null,
            property: eu.kasify.model.Property? = null,
            access: String? = null,
        ): Task =
            Task(
                id = task.id,
                property =
                    Property(
                        id = property?.id,
                        name = property?.name ?: task.propertyName ?: "",
                        address = property?.address,
                        zipcode = property?.zipcode,
                        city = property?.city,
                    ),
                bookingId = task.bookingId,
                taskType = task.taskType,
                status = task.status,
                assignedTo = task.assignedTo,
                assignedToName = task.assignedToName,
                notes = task.notes,
                jobReport = task.jobReport,
                from = task.from,
                to = task.to,
                createdAt = task.createdAt,
                startedAt = task.startedAt,
                completedAt = task.completedAt,
                cancelledAt = task.cancelledAt,
                nextBooking = nextBooking,
                access = access,
                publicToken = task.publicToken,
            )
    }
}
