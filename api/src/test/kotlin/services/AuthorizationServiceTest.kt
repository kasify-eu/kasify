package eu.kasify.services

import eu.kasify.db.PropertiesRepository
import eu.kasify.db.PropertyAccessRepository
import eu.kasify.db.model.PropertyAccessLevel
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AuthorizationServiceTest {
    private val service =
        AuthorizationService(
            propertyAccessRepository = mockk<PropertyAccessRepository>(),
            propertiesRepository = mockk<PropertiesRepository>(),
        )

    private val userA = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val userB = Uuid.parse("00000000-0000-0000-0000-0000000000b2")

    private fun access(
        level: PropertyAccessLevel?,
        isOwner: Boolean = false,
    ): AccessInfo =
        AccessInfo(
            hasAccess = true,
            role = level?.dbValue ?: if (isOwner) "owner" else "none",
            isAccountMember = isOwner,
            accessLevel = level,
        )

    @Test
    fun `owner always sees cleaning task`() {
        assertTrue(service.canSeeCleaningTask(access(level = null, isOwner = true), userA, userB))
    }

    @Test
    fun `manager always sees cleaning task`() {
        assertTrue(service.canSeeCleaningTask(access(PropertyAccessLevel.Manager), userA, userB))
    }

    @Test
    fun `staff sees cleaning task when assigned`() {
        assertTrue(service.canSeeCleaningTask(access(PropertyAccessLevel.Staff), userA, userA))
    }

    @Test
    fun `staff does not see cleaning task when not assigned`() {
        assertFalse(service.canSeeCleaningTask(access(PropertyAccessLevel.Staff), userA, userB))
    }

    @Test
    fun `contractor does not see cleaning task`() {
        assertFalse(service.canSeeCleaningTask(access(PropertyAccessLevel.Contractor), userA, userA))
    }
}
