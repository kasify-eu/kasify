package eu.kasify.model.api

import eu.kasify.services.AccessInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for SecuritySerializer recursive filtering.
 * Verifies that nested objects are filtered based on @VisibleTo annotations.
 */
class SecuritySerializerTest {
    // Test data classes
    @Serializable
    data class NestedInfo(
        val publicField: String,
        @VisibleTo(Visibility.OWNER)
        val ownerOnlyField: String?,
    )

    @Serializable
    data class TestEntity(
        val id: String,
        @SerialName("public_name")
        val publicName: String,
        @VisibleTo(Visibility.STAFF)
        @SerialName("staff_field")
        val staffField: String?,
        @VisibleTo(Visibility.OWNER)
        @SerialName("owner_field")
        val ownerField: String?,
        @SerialName("nested_info")
        val nestedInfo: NestedInfo? = null,
    )

    @Serializable
    data class EntityWithList(
        val id: String,
        @SerialName("nested_items")
        val nestedItems: List<NestedInfo>,
    )

    private val ownerAccess = AccessInfo(hasAccess = true, role = "owner", isAccountMember = true)
    private val staff = AccessInfo(hasAccess = true, role = "staff", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Staff)
    private val contractor = AccessInfo(hasAccess = true, role = "contractor", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Contractor)

    // ============================================================
    // Basic Filtering Tests
    // ============================================================

    @Test
    fun `owner sees all fields`() {
        val entity =
            TestEntity(
                id = "123",
                publicName = "Test",
                staffField = "staff-data",
                ownerField = "owner-data",
            )

        val result = SecuritySerializer.serialize(entity, ownerAccess)
        val json = result as JsonObject

        assertNotNull(json["id"])
        assertNotNull(json["public_name"])
        assertNotNull(json["staff_field"])
        assertNotNull(json["owner_field"])

        assertEquals("123", json["id"]?.jsonPrimitive?.content)
        assertEquals("Test", json["public_name"]?.jsonPrimitive?.content)
        assertEquals("staff-data", json["staff_field"]?.jsonPrimitive?.content)
        assertEquals("owner-data", json["owner_field"]?.jsonPrimitive?.content)
    }

    @Test
    fun `staff sees filtered fields`() {
        val entity =
            TestEntity(
                id = "123",
                publicName = "Test",
                staffField = "staff-data",
                ownerField = "owner-data",
            )

        val result = SecuritySerializer.serialize(entity, staff)
        val json = result as JsonObject

        // Staff should see public and staff fields
        assertNotNull(json["id"])
        assertNotNull(json["public_name"])
        assertNotNull(json["staff_field"])

        // Staff should NOT see owner-only fields
        assertNull(json["owner_field"])
    }

    @Test
    fun `contractor does NOT see staff fields`() {
        val entity =
            TestEntity(
                id = "123",
                publicName = "Test",
                staffField = "staff-data",
                ownerField = "owner-data",
            )

        val result = SecuritySerializer.serialize(entity, contractor)
        val json = result as JsonObject

        // Contractor should see public fields
        assertNotNull(json["id"])
        assertNotNull(json["public_name"])

        // Contractor should NOT see staff or owner fields
        assertNull(json["staff_field"])
        assertNull(json["owner_field"])
    }

    // ============================================================
    // Nested Object Filtering Tests (The Key Feature)
    // ============================================================

    @Test
    fun `owner sees nested object owner-only fields`() {
        val entity =
            TestEntity(
                id = "123",
                publicName = "Test",
                staffField = "staff-data",
                ownerField = "owner-data",
                nestedInfo =
                    NestedInfo(
                        publicField = "nested-public",
                        ownerOnlyField = "nested-secret",
                    ),
            )

        val result = SecuritySerializer.serialize(entity, ownerAccess)
        val json = result as JsonObject
        val nested = json["nested_info"] as? JsonObject

        assertNotNull(nested)
        assertNotNull(nested["publicField"])
        assertNotNull(nested["ownerOnlyField"])
        assertEquals("nested-secret", nested["ownerOnlyField"]?.jsonPrimitive?.content)
    }

    @Test
    fun `staff does NOT see nested object owner-only fields`() {
        val entity =
            TestEntity(
                id = "123",
                publicName = "Test",
                staffField = "staff-data",
                ownerField = "owner-data",
                nestedInfo =
                    NestedInfo(
                        publicField = "nested-public",
                        ownerOnlyField = "nested-secret",
                    ),
            )

        val result = SecuritySerializer.serialize(entity, staff)
        val json = result as JsonObject
        val nested = json["nested_info"] as? JsonObject

        assertNotNull(nested)
        assertNotNull(nested["publicField"])
        assertEquals("nested-public", nested["publicField"]?.jsonPrimitive?.content)

        // CRITICAL: Nested owner-only field should be filtered
        assertNull(nested["ownerOnlyField"], "Nested owner-only field should be filtered for staff")
    }

    // ============================================================
    // List Filtering Tests
    // ============================================================

    @Test
    fun `owner sees all fields in list items`() {
        val entity =
            EntityWithList(
                id = "123",
                nestedItems =
                    listOf(
                        NestedInfo("public1", "secret1"),
                        NestedInfo("public2", "secret2"),
                    ),
            )

        val result = SecuritySerializer.serialize(entity, ownerAccess)
        val json = result as JsonObject
        val items = json["nested_items"] as? kotlinx.serialization.json.JsonArray

        assertNotNull(items)
        assertEquals(2, items.size)

        val item1 = items[0] as JsonObject
        assertNotNull(item1["publicField"])
        assertNotNull(item1["ownerOnlyField"])
    }

    @Test
    fun `staff sees filtered fields in list items`() {
        val entity =
            EntityWithList(
                id = "123",
                nestedItems =
                    listOf(
                        NestedInfo("public1", "secret1"),
                        NestedInfo("public2", "secret2"),
                    ),
            )

        val result = SecuritySerializer.serialize(entity, staff)
        val json = result as JsonObject
        val items = json["nested_items"] as? kotlinx.serialization.json.JsonArray

        assertNotNull(items)
        assertEquals(2, items.size)

        val item1 = items[0] as JsonObject
        assertNotNull(item1["publicField"])
        assertNull(item1["ownerOnlyField"], "Owner-only field should be filtered in list items")
    }

    // ============================================================
    // Multi-Property Access Tests
    // ============================================================

    @Test
    fun `serializeWithPropertyAccess filters based on per-property access`() {
        val propertyA = Uuid.parse("10000000-0000-0000-0000-000000000001")
        val propertyB = Uuid.parse("20000000-0000-0000-0000-000000000002")

        data class TestItem(
            val propertyId: Uuid,
            val ownerField: String,
        )

        @Serializable
        data class SerializableItem(
            val propertyId: Uuid,
            @VisibleTo(Visibility.OWNER)
            val ownerField: String,
        )

        val items =
            listOf(
                SerializableItem(propertyA, "secret-a"),
                SerializableItem(propertyB, "secret-b"),
            )

        val accessByProperty =
            mapOf(
                propertyA to AccessInfo(hasAccess = true, role = "owner", isAccountMember = true),
                propertyB to AccessInfo(hasAccess = true, role = "staff", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Staff),
            )

        val result =
            SecuritySerializer.serializeWithPropertyAccess(
                items = items,
                getPropertyId = { it.propertyId },
                accessByProperty = accessByProperty,
            )

        assertEquals(2, result.size)

        // Property A (owner) should see owner field
        val itemA = result[0] as JsonObject
        assertNotNull(itemA["ownerField"], "Owner should see ownerField for property A")

        // Property B (staff) should NOT see owner field
        val itemB = result[1] as JsonObject
        assertNull(itemB["ownerField"], "Staff should NOT see ownerField for property B")
    }

    // ============================================================
    // Visibility Hierarchy Tests
    // ============================================================

    @Test
    fun `visibility levels work correctly`() {
        // PUBLIC < CONTRACTOR < STAFF < MANAGER < OWNER
        assertTrue(Visibility.PUBLIC.ordinal < Visibility.CONTRACTOR.ordinal)
        assertTrue(Visibility.CONTRACTOR.ordinal < Visibility.STAFF.ordinal)
        assertTrue(Visibility.STAFF.ordinal < Visibility.MANAGER.ordinal)
        assertTrue(Visibility.MANAGER.ordinal < Visibility.OWNER.ordinal)
    }

    @Test
    fun `ses_communications array serializes for manager and is gated from staff`() {
        val booking =
            Booking(
                id = Uuid.random(),
                property = Property(name = "P", role = "manager"),
                from = java.time.LocalDate.of(2026, 1, 1),
                to = java.time.LocalDate.of(2026, 1, 2),
                channel = eu.kasify.db.model.BookingChannel.Direct,
                channelId = "ref",
                checkinCode = "CODE",
                status = eu.kasify.db.model.BookingStatus.Confirmed,
                createdAt = java.time.LocalDate.of(2026, 1, 1),
                sesCommunications =
                    listOf(
                        SesCommunicationDto(
                            id = Uuid.random(),
                            type = "pv",
                            operation = "alta",
                            status = "accepted",
                            lote = "LOTE-1",
                        ),
                    ),
            )
        val manager =
            AccessInfo(hasAccess = true, role = "manager", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Manager)

        val managerJson = SecuritySerializer.serialize(booking, manager) as JsonObject
        assertNotNull(managerJson["ses_communications"], "manager should see ses_communications")

        val staffJson = SecuritySerializer.serialize(booking, staff) as JsonObject
        assertNull(staffJson["ses_communications"], "staff must not see ses_communications")
    }

    @Test
    fun `toVisibility converts AccessInfo correctly`() {
        val owner = AccessInfo(hasAccess = true, role = "owner", isAccountMember = true)
        val manager = AccessInfo(hasAccess = true, role = "manager", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Manager)
        val staffAccess = AccessInfo(hasAccess = true, role = "staff", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Staff)
        val contractorAccess = AccessInfo(hasAccess = true, role = "contractor", isAccountMember = false, accessLevel = eu.kasify.db.model.PropertyAccessLevel.Contractor)

        assertEquals(Visibility.OWNER, owner.toVisibility())
        assertEquals(Visibility.MANAGER, manager.toVisibility())
        assertEquals(Visibility.STAFF, staffAccess.toVisibility())
        assertEquals(Visibility.CONTRACTOR, contractorAccess.toVisibility())
    }
}
