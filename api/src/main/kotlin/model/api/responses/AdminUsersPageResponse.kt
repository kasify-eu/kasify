package eu.kasify.model.api.responses

import eu.kasify.model.api.User
import kotlinx.serialization.Serializable

@Serializable
data class AdminUsersPageResponse(
    val items: List<User>,
    override val page: Int?,
    override val size: Int?,
    override val total: Int?,
) : PaginatedResponse
