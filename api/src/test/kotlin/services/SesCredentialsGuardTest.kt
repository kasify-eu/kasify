package eu.kasify.services

import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.UsersRepository
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.model.User
import eu.kasify.services.notifications.NotificationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SesCredentialsGuardTest {
    private val accountId = Uuid.parse("00000000-0000-0000-0000-0000000000a1")
    private val integrationId = Uuid.parse("00000000-0000-0000-0000-0000000000f1")
    private val ownerId = Uuid.parse("00000000-0000-0000-0000-0000000000d1")

    private val owner =
        User(
            id = ownerId,
            accountId = accountId,
            name = "Jose",
            lastName = "Owner",
            email = "owner@example.com",
        )

    private fun buildIntegration(credentialsErrorCode: Int? = null): SesHospedajesIntegration =
        SesHospedajesIntegration(
            id = integrationId,
            accountIntegrationId = Uuid.parse("00000000-0000-0000-0000-0000000000f2"),
            accountId = accountId,
            arrendadorCode = "0000145750",
            sesUsername = "ses-user",
            sesPassword = "ses-pass",
            enabled = true,
            rhDirectBookingsEnabled = true,
            credentialsErrorCode = credentialsErrorCode,
        )

    private fun buildGuard(
        integrationsRepository: IntegrationsRepository = mockk(),
        usersRepository: UsersRepository = mockk(),
        notificationService: NotificationService = mockk(relaxUnitFun = true),
    ): SesCredentialsGuard = SesCredentialsGuard(integrationsRepository, usersRepository, notificationService)

    @Test
    fun `credential error codes cover the documented family`() {
        assertTrue(SesCredentialsGuard.isCredentialError(10103))
        assertTrue(SesCredentialsGuard.isCredentialError(10107))
        assertTrue(SesCredentialsGuard.isCredentialError(10119))
        assertTrue(SesCredentialsGuard.isCredentialError(10120))
        assertFalse(SesCredentialsGuard.isCredentialError(0))
        assertFalse(SesCredentialsGuard.isCredentialError(10121))
        assertFalse(SesCredentialsGuard.isCredentialError(10999))
    }

    @Test
    fun `onCredentialError notifies the owners on the first transition`() =
        runTest {
            val integrations: IntegrationsRepository = mockk()
            val users: UsersRepository = mockk()
            val notifications: NotificationService = mockk(relaxUnitFun = true)
            coEvery { integrations.markSesCredentialsError(integrationId, 10107, "Usuario incorrecto") } returns true
            coEvery { users.getUserIdsByAccountId(accountId) } returns listOf(ownerId)
            coEvery { users.getUsersByIds(listOf(ownerId)) } returns listOf(owner)
            val guard = buildGuard(integrations, users, notifications)
            val integration = buildIntegration()

            guard.onCredentialError(integration, 10107, "Usuario incorrecto")

            coVerify(exactly = 1) {
                notifications.enqueueSesCredentialsError(integration, 10107, "Usuario incorrecto", listOf(owner))
            }
        }

    @Test
    fun `onCredentialError does not re-notify when the flag is already set`() =
        runTest {
            val integrations: IntegrationsRepository = mockk()
            val users: UsersRepository = mockk()
            val notifications: NotificationService = mockk(relaxUnitFun = true)
            // Second failure in the same outage: the first-writer-wins update matched no row.
            coEvery { integrations.markSesCredentialsError(integrationId, 10107, any()) } returns false
            val guard = buildGuard(integrations, users, notifications)

            guard.onCredentialError(buildIntegration(), 10107, "Usuario incorrecto")

            coVerify(exactly = 0) { notifications.enqueueSesCredentialsError(any(), any(), any(), any()) }
        }

    @Test
    fun `onCredentialError never throws when persistence fails`() =
        runTest {
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.markSesCredentialsError(any(), any(), any()) } throws RuntimeException("db down")
            val guard = buildGuard(integrations)

            // Must not propagate — the SES submit outcome takes precedence over bookkeeping.
            guard.onCredentialError(buildIntegration(), 10107, "Usuario incorrecto")
        }

    @Test
    fun `onSesSuccess always clears via the DB regardless of the in-memory snapshot`() =
        runTest {
            val integrations: IntegrationsRepository = mockk()
            coEvery { integrations.clearSesCredentialsError(integrationId) } returns true
            val guard = buildGuard(integrations)

            // Both a flagged and a stale (null) snapshot hit the DB: the flag may have been written
            // concurrently after the caller loaded the integration, and gating on the snapshot
            // would leave the account paused right after SES proved the credentials work.
            guard.onSesSuccess(buildIntegration(credentialsErrorCode = 10107))
            guard.onSesSuccess(buildIntegration(credentialsErrorCode = null))
            coVerify(exactly = 2) { integrations.clearSesCredentialsError(integrationId) }
        }

    @Test
    fun `onCredentialError retries the notification when owner resolution fails transiently`() =
        runTest {
            val integrations: IntegrationsRepository = mockk()
            val users: UsersRepository = mockk()
            val notifications: NotificationService = mockk(relaxUnitFun = true)
            coEvery { integrations.markSesCredentialsError(integrationId, 10107, any()) } returns true
            coEvery { users.getUserIdsByAccountId(accountId) } throws
                RuntimeException("transient") andThenThrows
                RuntimeException("transient") andThen
                listOf(ownerId)
            coEvery { users.getUsersByIds(listOf(ownerId)) } returns listOf(owner)
            val guard = buildGuard(integrations, users, notifications)

            guard.onCredentialError(buildIntegration(), 10107, "Usuario incorrecto")

            // The transition fires only once per outage, so the notification must survive
            // transient failures instead of being lost with the consumed transition.
            coVerify(exactly = 1) { notifications.enqueueSesCredentialsError(any(), 10107, any(), listOf(owner)) }
        }
}
