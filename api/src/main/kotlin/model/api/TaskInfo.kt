package eu.kasify.model.api

import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class TaskInfo(
    val id: Uuid,
    @SerialName("task_type")
    val taskType: PropertyService,
    val status: TaskStatus,
    @SerialName("assigned_to")
    val assignedTo: Uuid? = null,
    @SerialName("assigned_to_name")
    val assignedToName: String? = null,
)
