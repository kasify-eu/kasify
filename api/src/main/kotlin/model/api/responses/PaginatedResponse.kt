package eu.kasify.model.api.responses

interface PaginatedResponse {
    val page: Int?
    val size: Int?
    val total: Int?
}
