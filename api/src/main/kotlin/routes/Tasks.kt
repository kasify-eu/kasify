package eu.kasify.routes
import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.BookingsRepository
import eu.kasify.db.CreateTaskRequest
import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.TasksFilters
import eu.kasify.db.TasksRepository
import eu.kasify.db.UsersRepository
import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.db.model.PropertyService
import eu.kasify.db.model.TaskStatus
import eu.kasify.model.api.SecuritySerializer
import eu.kasify.model.api.Task
import eu.kasify.model.api.requests.UpdateTaskRequest
import eu.kasify.model.api.responses.TasksResponse
import eu.kasify.routes.exceptions.BadParamRequestException
import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.routes.extensions.getPathProperty
import eu.kasify.routes.extensions.isAssignableToProperty
import eu.kasify.security.getKasifyUser
import eu.kasify.services.AccessInfo
import eu.kasify.services.AuthorizationService
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.ktor.ext.inject
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

private val TASK_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

/**
 * Render a task for a list response, applying the same role-based redaction as `GET /tasks/{id}`:
 * - Non-manager crew viewing a task that isn't assigned to them get a stripped object
 *   (id + window + property only).
 * - Everyone else gets the full task with `@VisibleTo` field filtering for their access level.
 *
 * Lists therefore never leak owner-only fields (booking_id, created_at, cancelled_at), and crew
 * only see the operational detail of foreign tasks down to the bare schedule + property.
 */
private fun serializeTaskForList(
    task: eu.kasify.model.Task,
    access: AccessInfo,
    userId: Uuid,
    property: eu.kasify.model.Property? = null,
): JsonElement {
    val isForeignToCrew =
        !access.isAccountMember &&
            access.accessLevel != PropertyAccessLevel.Manager &&
            task.assignedTo != userId
    if (isForeignToCrew) {
        return buildJsonObject {
            put("id", JsonPrimitive(task.id.toString()))
            put("from", JsonPrimitive(task.from.atZone(ZoneOffset.UTC).format(TASK_DATETIME_FORMATTER)))
            put("to", JsonPrimitive(task.to.atZone(ZoneOffset.UTC).format(TASK_DATETIME_FORMATTER)))
            put(
                "property",
                buildJsonObject {
                    put("id", JsonPrimitive((property?.id ?: task.propertyId).toString()))
                    put("name", JsonPrimitive(property?.name ?: task.propertyName ?: ""))
                },
            )
        }
    }
    return SecuritySerializer.serialize(
        Task.from(task, property = property, access = access.role),
        access,
    )
}

/**
 * Rejects a caller-supplied task assignee that is neither an owner nor a crew member of
 * [property]. Ids in [alreadyLinked] — values the owner already linked to this property/task,
 * such as the property's current default cleaner — are accepted unchanged, so re-sending existing
 * state keeps working even when that user has since left the crew.
 */
private suspend fun requireTaskAssignee(
    assigneeId: Uuid,
    property: eu.kasify.model.Property,
    alreadyLinked: Set<Uuid>,
    usersRepository: UsersRepository,
    propertyAccessRepository: PropertyAccessRepository,
) {
    if (assigneeId in alreadyLinked) return
    if (!isAssignableToProperty(assigneeId, property, usersRepository, propertyAccessRepository)) {
        throw BadParamRequestException(
            Errors.ERROR_TASK_ASSIGNEE_NOT_IN_PROPERTY,
            code = Errors.CODE_TASK_ASSIGNEE_NOT_IN_PROPERTY,
        )
    }
}

@OptIn(ExperimentalKtorApi::class)
fun Application.configureTasksRouting() {
    val propertiesRepository by inject<PropertiesRepository>()
    val tasksRepository by inject<TasksRepository>()
    val bookingsRepository by inject<BookingsRepository>()
    val authorizationService by inject<AuthorizationService>()
    val usersRepository by inject<UsersRepository>()
    val propertyAccessRepository by inject<PropertyAccessRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            // Create a task
            post("/tasks") {
                val user = call.getKasifyUser()
                val request = call.receive<eu.kasify.model.api.requests.CreateTaskRequest>()

                // Validate property access
                val property =
                    propertiesRepository.getProperty(request.propertyId)
                        ?: throw NotFoundException("Property not found")
                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember) {
                    throw ForbiddenException(
                        "Only property owners can create tasks.",
                        code = "task.owner_required",
                    )
                }

                val assignedTo = request.assignedTo ?: property.defaultCleanerId

                if (!request.from.isBefore(request.to)) {
                    throw BadParamRequestException(
                        "Scheduled start must be before end.",
                        code = "task.invalid_schedule",
                    )
                }

                // A task may only link a booking of its own property. Unknown and foreign booking
                // ids get the same error so the response does not reveal which ids exist.
                request.bookingId?.let { bookingId ->
                    val booking = bookingsRepository.getBooking(bookingId)
                    if (booking == null || booking.propertyId != request.propertyId) {
                        throw BadParamRequestException(
                            Errors.ERROR_TASK_BOOKING_NOT_IN_PROPERTY,
                            code = Errors.CODE_TASK_BOOKING_NOT_IN_PROPERTY,
                        )
                    }
                }

                // A caller-supplied assignee must be an owner or crew member of the property.
                // Sending the property's current default cleaner is the same as omitting it.
                request.assignedTo?.let {
                    requireTaskAssignee(
                        it,
                        property,
                        alreadyLinked = setOf(property.defaultCleanerId),
                        usersRepository = usersRepository,
                        propertyAccessRepository = propertyAccessRepository,
                    )
                }

                val task =
                    tasksRepository.create(
                        CreateTaskRequest(
                            propertyId = request.propertyId,
                            bookingId = request.bookingId,
                            taskType = request.taskType,
                            from = request.from,
                            to = request.to,
                            assignedTo = assignedTo,
                            notes = request.notes,
                        ),
                    )

                call.respond(
                    HttpStatusCode.Created,
                    SecuritySerializer.serialize(
                        Task.from(task, access = access.role),
                        access,
                    ),
                )
            }.describe {
                tag("Tasks")
                summary = "Create task"
                requestBody {
                    description = "Task fields. `task_type` is one of `cleaning`, `maintenance`, `pool`, `gardening`."
                    required = true
                    schema = jsonSchema<eu.kasify.model.api.requests.CreateTaskRequest>()
                }
                responses {
                    HttpStatusCode.Created {
                        description = "The created task"
                        schema = jsonSchema<Task>()
                    }
                    HttpStatusCode.BadRequest {
                        description =
                            "Invalid schedule (`task.invalid_schedule`), `booking_id` is unknown or belongs to " +
                            "another property (`task.booking_not_in_property`), or `assigned_to` is not an " +
                            "owner or crew member of the property (`task.assignee_not_in_property`)"
                    }
                    HttpStatusCode.Forbidden {
                        description = "Caller is not an account member. Returns `task.owner_required`"
                    }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }

            // List tasks with filters
            get("/tasks") {
                val user = call.getKasifyUser()
                val pageable = call.getPageableWithLimit()

                // Parse filters from query parameters
                val propertyId = call.parameters["property_id"]?.let { Uuid.parse(it) }
                val assignedTo = call.parameters["assigned_to"]?.let { Uuid.parse(it) }
                val status =
                    call.parameters["status"]?.let {
                        TaskStatus.entries.find { s -> s.name.equals(it, ignoreCase = true) }
                    }
                val taskType =
                    call.parameters["task_type"]?.let {
                        PropertyService.entries.find { t -> t.name.equals(it, ignoreCase = true) }
                    }
                val scheduledFrom =
                    call.parameters["scheduled_from"]?.let {
                        LocalDate.parse(it)
                    }
                val scheduledTo =
                    call.parameters["scheduled_to"]?.let {
                        LocalDate.parse(it)
                    }
                val includeCancelled = call.parameters["include_cancelled"]?.toBoolean() ?: false

                // If property_id is specified, validate access
                if (propertyId != null) {
                    val property =
                        propertiesRepository.getProperty(propertyId)
                            ?: throw NotFoundException("Property not found")
                    authorizationService.requireAccess(user, property)
                }

                // Build filters based on what's provided
                val filters =
                    if (propertyId != null) {
                        // Specific property requested - filter by that property only
                        TasksFilters(
                            propertyId = propertyId,
                            assignedTo = assignedTo,
                            status = status,
                            taskType = taskType,
                            scheduledFrom = scheduledFrom?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) },
                            scheduledTo = scheduledTo?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) },
                            includeCancelled = includeCancelled,
                        )
                    } else {
                        // No specific property - return all tasks from owned properties + assigned tasks
                        TasksFilters(
                            ownedByAccountId = user.accountId,
                            assignedTo = user.userId,
                            status = status,
                            taskType = taskType,
                            scheduledFrom = scheduledFrom?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) },
                            scheduledTo = scheduledTo?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) },
                            includeCancelled = includeCancelled,
                        )
                    }

                val tasks = tasksRepository.list(filters, pageable)

                // Resolve access per property (tasks may span owned + assigned properties when
                // no property_id is given) and serialize each task with role-based redaction.
                val accessByProperty =
                    authorizationService.getAccessForProperties(
                        user,
                        tasks.content.map { it.propertyId }.distinct(),
                    )
                val serializedTasks =
                    JsonArray(
                        tasks.content.mapNotNull { task ->
                            val access = accessByProperty[task.propertyId] ?: return@mapNotNull null
                            if (!access.hasAccess) return@mapNotNull null
                            serializeTaskForList(task, access, user.userId)
                        },
                    )

                call.respond(
                    buildJsonObject {
                        put("tasks", serializedTasks)
                        put("page", JsonPrimitive(tasks.details.pageIndex ?: 0))
                        put("size", JsonPrimitive(tasks.details.elementsPerPage ?: 0))
                        put("total", JsonPrimitive(tasks.details.totalElements ?: 0))
                    },
                )
            }.describe {
                tag("Tasks")
                summary = "List tasks"
                parameters {
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100)" }
                    query("property_id") { description = "Restrict to tasks for a single property" }
                    query("assigned_to") { description = "Filter by assigned user id" }
                    query("status") { description = "Filter by task status (`pending`, `in_progress`, `completed`, `cancelled`)" }
                    query("task_type") { description = "Filter by task type (`cleaning`, `maintenance`, `pool`, `gardening`)" }
                    query("scheduled_from") { description = "Only tasks scheduled on or after this date (ISO-8601)" }
                    query("scheduled_to") { description = "Only tasks scheduled on or before this date (ISO-8601)" }
                    query("include_cancelled") { description = "Include cancelled tasks when true (default false)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated tasks. When `property_id` is omitted, returns tasks from owned properties plus tasks assigned to the caller. Fields are filtered per the caller's access level (owner-only fields like `booking_id`, `created_at`, `cancelled_at` are hidden from crew); staff/contractor crew viewing a task not assigned to them receive a stripped item (id, from, to, property only)."
                        schema = jsonSchema<TasksResponse>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                    HttpStatusCode.NotFound { description = "`property_id` refers to an unknown property" }
                }
            }

            // Get task by ID
            get("/tasks/{id}") {
                val user = call.getKasifyUser()
                val taskId =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing task ID")

                val task = tasksRepository.getById(taskId) ?: throw NotFoundException("Task not found")

                // Validate access to the property
                val property =
                    propertiesRepository.getProperty(task.propertyId)
                        ?: throw NotFoundException("Property not found")

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw ForbiddenException("Access to property denied.")
                }

                // Non-manager crew viewing a task that isn't assigned to them get
                // a stripped response (id + window + property). Mirrors the
                // foreign-task redaction applied to the calendar payloads.
                val isForeignToCrew =
                    !access.isAccountMember &&
                        access.accessLevel != PropertyAccessLevel.Manager &&
                        task.assignedTo != user.userId
                if (isForeignToCrew) {
                    call.respond(
                        buildJsonObject {
                            put("id", JsonPrimitive(task.id.toString()))
                            put(
                                "from",
                                JsonPrimitive(task.from.atZone(ZoneOffset.UTC).format(TASK_DATETIME_FORMATTER)),
                            )
                            put(
                                "to",
                                JsonPrimitive(task.to.atZone(ZoneOffset.UTC).format(TASK_DATETIME_FORMATTER)),
                            )
                            put(
                                "property",
                                buildJsonObject {
                                    put("id", JsonPrimitive(property.id.toString()))
                                    put("name", JsonPrimitive(property.name))
                                },
                            )
                        },
                    )
                    return@get
                }

                val nextBooking =
                    if (task.taskType == PropertyService.Cleaning) {
                        bookingsRepository
                            .getNextBooking(task.propertyId, task.from.toLocalDate())
                            ?.let {
                                eu.kasify.model.api.Booking
                                    .from(it)
                            }
                    } else {
                        null
                    }

                call.respond(
                    SecuritySerializer.serialize(
                        Task.from(task, nextBooking, property, access.role),
                        access,
                    ),
                )
            }.describe {
                tag("Tasks")
                summary = "Get task"
                parameters {
                    path("id") { description = "Task id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The task. Staff/Contractor crew viewing a task that isn't assigned to them receive a stripped response (id, from, to, property only). Contractors viewing a cleaning task assigned to them receive the next-booking context embedded in the task."
                        schema = jsonSchema<Task>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks access to the task's property" }
                    HttpStatusCode.NotFound { description = "Task or property not found" }
                }
            }

            // Update task (assign, change status, add notes, job report)
            patch("/tasks/{id}") {
                val user = call.getKasifyUser()
                val taskId =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing task ID")

                val task = tasksRepository.getById(taskId) ?: throw NotFoundException("Task not found")

                val property =
                    propertiesRepository.getProperty(task.propertyId)
                        ?: throw NotFoundException("Property not found")
                val access = authorizationService.getAccess(user, property)

                if (!access.hasAccess) {
                    throw ForbiddenException("Access to property denied.")
                }

                val request = call.receive<UpdateTaskRequest>()

                val isOwner = access.isAccountMember

                val hasScheduleUpdate = request.from != null || request.to != null

                // Crew can only update jobReport
                if (!isOwner) {
                    // Non-managers may only update tasks assigned to themselves.
                    if (access.accessLevel != PropertyAccessLevel.Manager && task.assignedTo != user.userId) {
                        throw ForbiddenException(
                            "Only the assigned crew member can update this task.",
                            code = "task.not_assignee",
                        )
                    }
                    if (request.assignedTo != null ||
                        request.notes != null ||
                        request.status != null ||
                        hasScheduleUpdate
                    ) {
                        throw ForbiddenException(
                            "Crew members can only update the job report.",
                            code = "task.crew_restricted",
                        )
                    }
                }

                // A new assignee must be an owner or crew member of the property. Re-sending the
                // task's current assignee or the property's current default cleaner is accepted
                // unchanged. Validated before any field is written.
                if (isOwner) {
                    request.assignedTo?.let {
                        requireTaskAssignee(
                            it,
                            property,
                            alreadyLinked = setOf(task.assignedTo, property.defaultCleanerId),
                            usersRepository = usersRepository,
                            propertyAccessRepository = propertyAccessRepository,
                        )
                    }
                }

                var updatedTask = task
                if (isOwner) {
                    request.assignedTo?.let {
                        updatedTask = tasksRepository.assign(taskId, it) ?: throw NotFoundException("Task not found")
                    }
                    request.notes?.let {
                        updatedTask = tasksRepository.updateNotes(taskId, it) ?: throw NotFoundException("Task not found")
                    }
                    request.status?.let {
                        updatedTask = tasksRepository.updateStatus(taskId, it) ?: throw NotFoundException("Task not found")
                    }
                    if (hasScheduleUpdate) {
                        val newFrom = request.from ?: updatedTask.from
                        val newTo = request.to ?: updatedTask.to
                        if (!newFrom.isBefore(newTo)) {
                            throw BadParamRequestException(
                                "Scheduled start must be before end.",
                                code = "task.invalid_schedule",
                            )
                        }
                        updatedTask =
                            tasksRepository.updateSchedule(
                                taskId,
                                request.from,
                                request.to,
                            ) ?: throw NotFoundException("Task not found")
                    }
                }
                request.jobReport?.let {
                    updatedTask = tasksRepository.updateJobReport(taskId, it) ?: throw NotFoundException("Task not found")
                }

                call.respond(
                    SecuritySerializer.serialize(
                        Task.from(updatedTask, access = access.role),
                        access,
                    ),
                )
            }.describe {
                tag("Tasks")
                summary = "Update task"
                description = "Owners can update all fields. Managers and the assigned crew member can update `job_report`; other crew receive 403."
                parameters {
                    path("id") { description = "Task id" }
                }
                requestBody {
                    description = "Fields to update on the task. All fields are optional."
                    required = true
                    schema = jsonSchema<UpdateTaskRequest>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The updated task"
                        schema = jsonSchema<Task>()
                    }
                    HttpStatusCode.BadRequest {
                        description =
                            "Invalid schedule (`task.invalid_schedule`) — start time must be before end time — " +
                            "or `assigned_to` is not an owner or crew member of the property " +
                            "(`task.assignee_not_in_property`)"
                    }
                    HttpStatusCode.Forbidden {
                        description =
                            "Crew tried to update owner-only fields (`task.crew_restricted`), " +
                            "non-assignee crew tried to update the task (`task.not_assignee`), " +
                            "or caller lacks property access"
                    }
                    HttpStatusCode.NotFound { description = "Task or property not found" }
                }
            }

            // Delete task
            delete("/tasks/{id}") {
                val user = call.getKasifyUser()
                val taskId =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing task ID")

                val task = tasksRepository.getById(taskId) ?: throw NotFoundException("Task not found")

                // Validate owner access
                val property =
                    propertiesRepository.getProperty(task.propertyId)
                        ?: throw NotFoundException("Property not found")
                authorizationService.requireOwner(user, property)

                tasksRepository.delete(taskId)
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                tag("Tasks")
                summary = "Delete task"
                parameters {
                    path("id") { description = "Task id" }
                }
                responses {
                    HttpStatusCode.NoContent { description = "Task deleted" }
                    HttpStatusCode.Forbidden { description = "Caller is not the property owner" }
                    HttpStatusCode.NotFound { description = "Task or property not found" }
                }
            }

            // Start a task (crew can start their assigned tasks)
            post("/tasks/{id}/start") {
                val user = call.getKasifyUser()
                val taskId =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing task ID")

                val task = tasksRepository.getById(taskId) ?: throw NotFoundException("Task not found")

                // Validate access (crew or owner)
                val property =
                    propertiesRepository.getProperty(task.propertyId)
                        ?: throw NotFoundException("Property not found")
                authorizationService.requireAccess(user, property)

                // Only assigned user or owner can start
                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && task.assignedTo != user.userId) {
                    throw BadParamRequestException(
                        "Only the assigned crew member or owner can start this task",
                        code = "task.not_assigned",
                    )
                }

                if (task.status != TaskStatus.pending) {
                    throw BadParamRequestException("Task must be pending to start", code = "task.not_pending")
                }

                val updatedTask = tasksRepository.updateStatus(taskId, TaskStatus.in_progress)
                call.respond(
                    SecuritySerializer.serialize(
                        Task.from(updatedTask!!, access = access.role),
                        access,
                    ),
                )
            }.describe {
                tag("Tasks")
                summary = "Start task"
                description = "Marks the task as `in_progress`. Only the assigned crew member or an owner can start a task, and the task must be in `pending` status."
                parameters {
                    path("id") { description = "Task id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The task with its status set to `in_progress`"
                        schema = jsonSchema<Task>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Returns `task.not_assigned` if the caller is neither assigned nor owner, or `task.not_pending` if the task is not in `pending` status"
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks property access" }
                    HttpStatusCode.NotFound { description = "Task or property not found" }
                }
            }

            // Complete a task (crew can complete their assigned tasks)
            post("/tasks/{id}/complete") {
                val user = call.getKasifyUser()
                val taskId =
                    call.parameters["id"]?.let { Uuid.parse(it) }
                        ?: throw MissingRequestParameterException("Missing task ID")

                val task = tasksRepository.getById(taskId) ?: throw NotFoundException("Task not found")

                // Validate access (crew or owner)
                val property =
                    propertiesRepository.getProperty(task.propertyId)
                        ?: throw NotFoundException("Property not found")
                authorizationService.requireAccess(user, property)

                // Only assigned user or owner can complete
                val access = authorizationService.getAccess(user, property)
                if (!access.isAccountMember && task.assignedTo != user.userId) {
                    throw BadParamRequestException(
                        "Only the assigned crew member or owner can complete this task",
                        code = "task.not_assigned",
                    )
                }

                if (task.status != TaskStatus.in_progress) {
                    throw BadParamRequestException("Task must be in progress to complete", code = "task.not_in_progress")
                }

                val updatedTask = tasksRepository.updateStatus(taskId, TaskStatus.completed)
                call.respond(
                    SecuritySerializer.serialize(
                        Task.from(updatedTask!!, access = access.role),
                        access,
                    ),
                )
            }.describe {
                tag("Tasks")
                summary = "Complete task"
                description = "Marks the task as `completed`. Only the assigned crew member or an owner can complete a task, and the task must be in `in_progress` status."
                parameters {
                    path("id") { description = "Task id" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The task with its status set to `completed`"
                        schema = jsonSchema<Task>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Returns `task.not_assigned` if the caller is neither assigned nor owner, or `task.not_in_progress` if the task is not in `in_progress` status"
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks property access" }
                    HttpStatusCode.NotFound { description = "Task or property not found" }
                }
            }

            // Get tasks for a property
            get("/properties/{id}/tasks") {
                val user = call.getKasifyUser()
                val property = call.getPathProperty(propertiesRepository)
                val pageable = call.getPageableWithLimit()

                val access = authorizationService.getAccess(user, property)
                if (!access.hasAccess) {
                    throw ForbiddenException("Access to property denied.")
                }

                val status =
                    call.parameters["status"]?.let {
                        TaskStatus.entries.find { s -> s.name.equals(it, ignoreCase = true) }
                    }
                val includeCancelled = call.parameters["include_cancelled"]?.toBoolean() ?: false

                val tasks =
                    tasksRepository.list(
                        TasksFilters(
                            propertyId = property.id,
                            status = status,
                            includeCancelled = includeCancelled,
                        ),
                        pageable,
                    )

                val serializedTasks =
                    JsonArray(
                        tasks.content.map { task ->
                            serializeTaskForList(task, access, user.userId, property)
                        },
                    )

                call.respond(
                    buildJsonObject {
                        put("tasks", serializedTasks)
                        put("page", JsonPrimitive(tasks.details.pageIndex ?: 0))
                        put("size", JsonPrimitive(tasks.details.elementsPerPage ?: 0))
                        put("total", JsonPrimitive(tasks.details.totalElements ?: 0))
                    },
                )
            }.describe {
                tag("Tasks")
                summary = "List property tasks"
                parameters {
                    path("id") { description = "Property id" }
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 100)" }
                    query("status") { description = "Filter by task status" }
                    query("include_cancelled") { description = "Include cancelled tasks when true (default false)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated tasks for the property. Fields are filtered per the caller's access level (owner-only fields like `booking_id`, `created_at`, `cancelled_at` are hidden from crew); staff/contractor crew viewing a task not assigned to them receive a stripped item (id, from, to, property only)."
                        schema = jsonSchema<TasksResponse>()
                    }
                    HttpStatusCode.Forbidden { description = "Caller lacks property access" }
                    HttpStatusCode.NotFound { description = "Property not found" }
                }
            }
        }

        // Public task view (no auth required)
        get("/public/tasks/{token}") {
            val token =
                call.parameters["token"]
                    ?: throw MissingRequestParameterException("Missing task token")

            val task =
                tasksRepository.getByPublicToken(token)
                    ?: throw NotFoundException("Task not found")

            if (java.time.LocalDate
                    .now()
                    .isAfter(task.to.toLocalDate().plusDays(7))
            ) {
                throw ForbiddenException("This task link has expired.", code = "task.link_expired")
            }

            val property = propertiesRepository.getProperty(task.propertyId)

            val nextBooking =
                if (task.taskType == PropertyService.Cleaning) {
                    bookingsRepository
                        .getNextBooking(task.propertyId, task.from.toLocalDate())
                        ?.let {
                            eu.kasify.model.api.Booking
                                .from(it)
                        }
                } else {
                    null
                }

            val publicAccess =
                AccessInfo(
                    hasAccess = true,
                    role = "contractor",
                    isAccountMember = false,
                    accessLevel = PropertyAccessLevel.Contractor,
                )

            call.respond(
                SecuritySerializer.serialize(
                    Task.from(task, nextBooking, property, publicAccess.role),
                    publicAccess,
                ),
            )
        }.describe {
            tag("Tasks")
            summary = "Get task by public token"
            description = "Unauthenticated access for contractors via a shareable link. The token expires 7 days after the task's scheduled date."
            parameters {
                path("token") { description = "Public task token" }
            }
            responses {
                HttpStatusCode.OK {
                    description = "The task, rendered with contractor-level visibility"
                    schema = jsonSchema<Task>()
                }
                HttpStatusCode.Forbidden {
                    description = "Link expired. Returns `task.link_expired`"
                }
                HttpStatusCode.NotFound { description = "No task matches this token" }
            }
        }
    }
}
