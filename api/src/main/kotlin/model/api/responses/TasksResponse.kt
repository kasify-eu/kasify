package eu.kasify.model.api.responses

import eu.kasify.model.api.Task
import io.perracodex.exposed.pagination.Page
import kotlinx.serialization.Serializable

@Serializable
data class TasksResponse(
    val tasks: List<Task>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse {
    constructor(data: Page<eu.kasify.model.Task>?) : this(
        tasks = data?.content?.map { Task.from(it) } ?: emptyList(),
        page = data?.details?.pageIndex ?: 0,
        size = data?.details?.elementsPerPage ?: 0,
        total = data?.details?.totalElements ?: 0,
    )
}
