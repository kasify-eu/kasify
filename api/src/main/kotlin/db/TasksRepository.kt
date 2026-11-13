package eu.kasify.db
import eu.kasify.db.model.BookingsTable
import eu.kasify.db.model.CalendarEventsTable
import eu.kasify.db.model.EventType
import eu.kasify.db.model.PropertiesTable
import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import eu.kasify.db.model.TasksTable
import eu.kasify.db.model.UsersTable
import eu.kasify.model.Task
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.uuid.Uuid

data class CreateTaskRequest(
    val propertyId: Uuid,
    val bookingId: Uuid? = null,
    val taskType: PropertyService,
    val from: LocalDateTime,
    val to: LocalDateTime,
    val assignedTo: Uuid,
    val notes: String? = null,
)

data class TasksFilters(
    val propertyId: Uuid? = null,
    val assignedTo: Uuid? = null,
    val ownedByAccountId: Uuid? = null,
    val status: TaskStatus? = null,
    val taskType: PropertyService? = null,
    val scheduledFrom: LocalDate? = null,
    val scheduledTo: LocalDate? = null,
    val includeCancelled: Boolean = false,
)

open class TasksRepository {
    private val secureRandom = SecureRandom()

    private fun now() = CustomFunction<OffsetDateTime>("NOW", TasksTable.createdAt.columnType)

    private suspend fun getPropertyTimezone(propertyId: Uuid): ZoneId =
        dbQuery {
            PropertiesTable
                .select(PropertiesTable.tzone)
                .where { PropertiesTable.id eq propertyId }
                .firstOrNull()
                ?.get(PropertiesTable.tzone)
                ?.let { ZoneId.of(it) }
                ?: ZoneId.of("UTC")
        }

    /**
     * Create a new task with its associated calendar event
     */
    open suspend fun create(request: CreateTaskRequest): Task {
        val propertyZoneId = getPropertyTimezone(request.propertyId)

        val fromDateTime =
            request.from
                .atZone(propertyZoneId)
                .toOffsetDateTime()

        val toDateTime =
            request.to
                .atZone(propertyZoneId)
                .toOffsetDateTime()

        val publicToken = generateToken()

        val taskId =
            dbQuery {
                // First create the calendar event
                val calendarEventResult =
                    CalendarEventsTable.insertReturning {
                        it[CalendarEventsTable.propertyId] = request.propertyId
                        it[CalendarEventsTable.from] = fromDateTime
                        it[CalendarEventsTable.to] = toDateTime
                        it[CalendarEventsTable.role] = EventType.task
                    }

                val calendarEventId = calendarEventResult.first()[CalendarEventsTable.id]

                // Then create the task
                val taskResult =
                    TasksTable.insertReturning {
                        it[TasksTable.calendarEventId] = calendarEventId
                        it[TasksTable.propertyId] = request.propertyId
                        it[TasksTable.bookingId] = request.bookingId
                        it[TasksTable.taskType] = request.taskType
                        it[TasksTable.status] = TaskStatus.pending
                        it[TasksTable.assignedTo] = request.assignedTo
                        it[TasksTable.notes] = request.notes
                        it[TasksTable.publicToken] = publicToken
                    }

                taskResult.first()[TasksTable.id]
            }

        return getById(taskId)!!
    }

    /**
     * Get a task by ID with all related data
     */
    open suspend fun getById(id: Uuid): Task? =
        dbQuery {
            TasksTable
                .innerJoin(CalendarEventsTable, { TasksTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { TasksTable.propertyId }, { PropertiesTable.id })
                .innerJoin(UsersTable, { TasksTable.assignedTo }, { UsersTable.id })
                .selectAll()
                .where {
                    (TasksTable.id eq id) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstOrNull()
                ?.let { Task.from(it) }
        }

    /**
     * Get a task by its public token
     */
    open suspend fun getByPublicToken(token: String): Task? =
        dbQuery {
            TasksTable
                .innerJoin(CalendarEventsTable, { TasksTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { TasksTable.propertyId }, { PropertiesTable.id })
                .innerJoin(UsersTable, { TasksTable.assignedTo }, { UsersTable.id })
                .selectAll()
                .where {
                    (TasksTable.publicToken eq token) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstOrNull()
                ?.let { Task.from(it) }
        }

    /**
     * List tasks with filters
     */
    open suspend fun list(
        filters: TasksFilters,
        pageable: Pageable? = null,
    ): Page<Task> =
        dbQuery {
            val query =
                TasksTable
                    .innerJoin(CalendarEventsTable, { TasksTable.calendarEventId }, { CalendarEventsTable.id })
                    .innerJoin(PropertiesTable, { TasksTable.propertyId }, { PropertiesTable.id })
                    .innerJoin(UsersTable, { TasksTable.assignedTo }, { UsersTable.id })
                    .selectAll()

            var whereCondition: Op<Boolean> = PropertiesTable.deletedAt.isNull()

            if (!filters.includeCancelled) {
                whereCondition = whereCondition and TasksTable.cancelledAt.isNull()
            }

            filters.propertyId?.let {
                whereCondition = whereCondition and (TasksTable.propertyId eq it)
            }

            // Handle ownedByAccountId and assignedTo with OR logic when both are present
            if (filters.ownedByAccountId != null && filters.assignedTo != null) {
                // Return tasks from owned properties OR tasks assigned to user
                whereCondition = whereCondition and (
                    (PropertiesTable.accountId eq filters.ownedByAccountId) or
                        (TasksTable.assignedTo eq filters.assignedTo)
                )
            } else {
                filters.ownedByAccountId?.let {
                    whereCondition = whereCondition and (PropertiesTable.accountId eq it)
                }

                filters.assignedTo?.let {
                    whereCondition = whereCondition and (TasksTable.assignedTo eq it)
                }
            }

            filters.status?.let {
                whereCondition = whereCondition and (TasksTable.status eq it)
            }

            filters.taskType?.let {
                whereCondition = whereCondition and (TasksTable.taskType eq it)
            }

            filters.scheduledFrom?.let {
                whereCondition = whereCondition and (CalendarEventsTable.from.date() greaterEq it.toKotlinLocalDate())
            }

            filters.scheduledTo?.let {
                whereCondition = whereCondition and (CalendarEventsTable.from.date() lessEq it.toKotlinLocalDate())
            }

            query
                .where(whereCondition)
                .orderBy(CalendarEventsTable.from to SortOrder.ASC)
                .paginate(pageable = pageable, map = Task.Companion)
        }

    /**
     * List tasks for a specific property
     */
    open suspend fun listByProperty(
        propertyId: Uuid,
        includeCancelled: Boolean = false,
        pageable: Pageable? = null,
    ): Page<Task> = list(TasksFilters(propertyId = propertyId, includeCancelled = includeCancelled), pageable)

    /**
     * List tasks assigned to a specific user
     */
    open suspend fun listByAssignee(
        userId: Uuid,
        includeCancelled: Boolean = false,
        pageable: Pageable? = null,
    ): Page<Task> = list(TasksFilters(assignedTo = userId, includeCancelled = includeCancelled), pageable)

    /**
     * Update task status
     */
    open suspend fun updateStatus(
        id: Uuid,
        status: TaskStatus,
    ): Task? {
        dbQuery {
            TasksTable.update({ TasksTable.id eq id }) {
                it[TasksTable.status] = status
                when (status) {
                    TaskStatus.in_progress -> it[startedAt] = now()
                    TaskStatus.completed -> it[completedAt] = now()
                    else -> {}
                }
            }
        }
        return getById(id)
    }

    /**
     * Assign task to a user
     */
    open suspend fun assign(
        id: Uuid,
        userId: Uuid,
    ): Task? {
        dbQuery {
            TasksTable.update({ TasksTable.id eq id }) {
                it[assignedTo] = userId
            }
        }
        return getById(id)
    }

    /**
     * Update task notes
     */
    open suspend fun updateNotes(
        id: Uuid,
        notes: String?,
    ): Task? {
        dbQuery {
            TasksTable.update({ TasksTable.id eq id }) {
                it[TasksTable.notes] = notes
            }
        }
        return getById(id)
    }

    /**
     * Update the task schedule (start and/or end datetime).
     * The schedule is stored on the linked calendar event, so this updates
     * `calendar_events.from_datetime` / `to_datetime` accordingly.
     *
     * Only the provided fields are changed; the others are kept from the
     * current calendar event row, interpreted in the property timezone.
     */
    open suspend fun updateSchedule(
        id: Uuid,
        from: LocalDateTime?,
        to: LocalDateTime?,
    ): Task? {
        if (from == null && to == null) {
            return getById(id)
        }
        val task = getById(id) ?: return null
        val propertyZoneId = getPropertyTimezone(task.propertyId)

        val newFrom = from ?: task.from
        val newTo = to ?: task.to

        val fromDateTime = newFrom.atZone(propertyZoneId).toOffsetDateTime()
        val toDateTime = newTo.atZone(propertyZoneId).toOffsetDateTime()

        dbQuery {
            CalendarEventsTable.update({ CalendarEventsTable.id eq task.calendarEventId }) {
                it[CalendarEventsTable.from] = fromDateTime
                it[CalendarEventsTable.to] = toDateTime
            }
        }
        return getById(id)
    }

    /**
     * Update task job report
     */
    open suspend fun updateJobReport(
        id: Uuid,
        jobReport: String?,
    ): Task? {
        dbQuery {
            TasksTable.update({ TasksTable.id eq id }) {
                it[TasksTable.jobReport] = jobReport
            }
        }
        return getById(id)
    }

    /**
     * Cancel a task (soft delete)
     */
    open suspend fun cancel(id: Uuid): Boolean {
        val task = getById(id) ?: return false
        dbQuery {
            TasksTable.update({ TasksTable.id eq id }) {
                it[cancelledAt] = now()
            }
        }
        return true
    }

    /**
     * Hard delete a task and its calendar event
     */
    open suspend fun delete(id: Uuid): Boolean {
        val task = getById(id) ?: return false
        return dbQuery {
            // Delete task first (FK constraint)
            val deleted = TasksTable.deleteWhere { TasksTable.id eq id } > 0
            if (deleted) {
                // Then delete the calendar event
                CalendarEventsTable.deleteWhere { CalendarEventsTable.id eq task.calendarEventId }
            }
            deleted
        }
    }

    /**
     * Get the cleaning task for a booking.
     *
     * Only tasks on the booking's own property count: a task row linking a booking of another
     * property (e.g. another account's booking id) is never surfaced as that booking's task.
     */
    open suspend fun getCleaningTaskByBookingId(bookingId: Uuid): Task? =
        dbQuery {
            TasksTable
                .innerJoin(CalendarEventsTable, { TasksTable.calendarEventId }, { CalendarEventsTable.id })
                .innerJoin(PropertiesTable, { TasksTable.propertyId }, { PropertiesTable.id })
                .innerJoin(UsersTable, { TasksTable.assignedTo }, { UsersTable.id })
                .innerJoin(BookingsTable, { TasksTable.bookingId }, { BookingsTable.id })
                .selectAll()
                .where {
                    (TasksTable.bookingId eq bookingId) and
                        (TasksTable.propertyId eq BookingsTable.propertyId) and
                        (TasksTable.taskType eq PropertyService.Cleaning) and
                        (TasksTable.cancelledAt.isNull()) and
                        (PropertiesTable.deletedAt.isNull())
                }.firstOrNull()
                ?.let { Task.from(it) }
        }

    /**
     * Cancel tasks linked to a booking (for when booking is cancelled).
     *
     * Only tasks on the booking's own property are cancelled, so a task elsewhere that links the
     * booking id is never touched.
     */
    open suspend fun cancelTasksForBooking(bookingId: Uuid): Int =
        dbQuery {
            TasksTable.update({
                (TasksTable.bookingId eq bookingId) and
                    (
                        TasksTable.propertyId inSubQuery
                            BookingsTable
                                .select(BookingsTable.propertyId)
                                .where { BookingsTable.id eq bookingId }
                    )
            }) {
                it[cancelledAt] = now()
            }
        }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
