package eu.kasify.routes.extensions

import io.ktor.server.application.ApplicationCall
import io.perracodex.exposed.pagination.Pageable

/**
 * Maximum allowed page size to prevent excessive memory usage and performance degradation
 */
private const val MAX_PAGE_SIZE = 100

/**
 * Extension function to get Pageable with enforced maximum page size.
 *
 * This wrapper ensures that pagination requests cannot exceed MAX_PAGE_SIZE items
 * per page, protecting the API from performance issues and resource exhaustion.
 *
 * If only 'size' is provided without 'page', defaults to page 0.
 *
 * @return Pageable object with size capped at MAX_PAGE_SIZE, or null if no pagination requested
 */
fun ApplicationCall.getPageableWithLimit(
    maxPageSize: Int = MAX_PAGE_SIZE,
): Pageable {
    val sizeParam = parameters["size"]?.toIntOrNull()
    val pageParam = parameters["page"]?.toIntOrNull()

    // Build pageable: default page to 0 if only size is provided
    var page = pageParam ?: 0
    var size = sizeParam ?: maxPageSize // size is required if page is provided

    if (page <= 0) {
        page = 0
    }
    if (size <= 0) {
        size = maxPageSize
    }

    // Enforce maximum page size
    val cappedSize = if (size > maxPageSize) maxPageSize else size

    return Pageable(page = page, position = null, size = cappedSize)
}
