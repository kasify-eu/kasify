package eu.kasify.db.model

import kotlinx.serialization.Serializable

@Suppress("EnumEntryName", "ktlint:standard:enum-entry-name-case")
@Serializable
enum class TaskStatus {
    pending,
    in_progress,
    completed,
}
