package eu.kasify.model.api

/**
 * Visibility levels for role-based field filtering.
 * Levels are hierarchical: OWNER can see everything, PUBLIC can see the least.
 *
 * Hierarchy: PUBLIC < CONTRACTOR < STAFF < MANAGER < OWNER
 */
enum class Visibility {
    /**
     * Visible to anyone with property access.
     * Default visibility if no @VisibleTo annotation is present.
     */
    PUBLIC,

    /**
     * Visible to contractors, staff, managers, and owners.
     * Use for basic operational data external workers need.
     */
    CONTRACTOR,

    /**
     * Visible to staff members, managers, and owners.
     * Use for operational data regular employees need.
     */
    STAFF,

    /**
     * Visible to property managers and owners.
     * Use for operational data managers need but not general staff.
     */
    MANAGER,

    /**
     * Visible only to property owners.
     * Use for PII, financial data, access codes, etc.
     */
    OWNER,
}

/**
 * Annotation to specify minimum visibility level required to see a field.
 * Fields without this annotation default to PUBLIC visibility.
 *
 * Example:
 * ```
 * @Serializable
 * class Booking(
 *     val id: Uuid,  // PUBLIC by default
 *
 *     @VisibleTo(Visibility.STAFF)
 *     val guestFirstName: String?,
 *
 *     @VisibleTo(Visibility.OWNER)
 *     val guestPhone: String?,
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class VisibleTo(
    val level: Visibility,
)
