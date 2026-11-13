package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.NotificationStatus
import eu.kasify.model.NotificationType
import eu.kasify.model.email.Locale
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class NotificationsRepositoryTest : BaseRouteDBTests() {
    private val repository = NotificationsRepository()
    private val payload = NotificationPayload("invitation", Locale.EN, mapOf("property_name" to "Casa"))

    private fun newRow(channel: NotificationChannelType = NotificationChannelType.Email) =
        NewNotification(
            groupId = Uuid.random(),
            userId = TEST_ACCOUNT_1_USER_1.id,
            emailOverride = null,
            eventType = NotificationType.Invitation,
            channel = channel,
            payload = payload,
        )

    @Test
    fun `enqueue then claim returns the row and flips it to processing`() =
        runBlocking {
            assertEquals(1, repository.enqueue(listOf(newRow())))

            val claimed = repository.claimBatch(10)
            assertEquals(1, claimed.size)
            assertEquals(payload.placeholders, claimed[0].payload.placeholders)

            // The claimed row is now `processing`, so a second claim returns nothing.
            assertTrue(repository.claimBatch(10).isEmpty())
            assertEquals(NotificationStatus.Processing, repository.getById(claimed[0].id)!!.status)
        }

    @Test
    fun `markSent makes the row terminal`() =
        runBlocking {
            repository.enqueue(listOf(newRow()))
            val claimed = repository.claimBatch(10).single()

            repository.markSent(claimed.id, attemptCount = 1)

            val row = repository.getById(claimed.id)!!
            assertEquals(NotificationStatus.Sent, row.status)
            assertEquals(1, row.attemptCount)
        }

    @Test
    fun `markRetry reschedules into the future so it is not immediately reclaimed`() =
        runBlocking {
            repository.enqueue(listOf(newRow()))
            val claimed = repository.claimBatch(10).single()

            repository.markRetry(claimed.id, attemptCount = 1, error = "boom", nextAttemptAt = OffsetDateTime.now().plusHours(1))

            val row = repository.getById(claimed.id)!!
            assertEquals(NotificationStatus.Pending, row.status)
            assertEquals(1, row.attemptCount)
            assertEquals("boom", row.lastError)
            // Not due yet → not claimed.
            assertTrue(repository.claimBatch(10).isEmpty())
        }

    @Test
    fun `markDead makes the row terminal`() =
        runBlocking {
            repository.enqueue(listOf(newRow()))
            val claimed = repository.claimBatch(10).single()

            repository.markDead(claimed.id, attemptCount = 5, error = "gave up")

            assertEquals(NotificationStatus.Dead, repository.getById(claimed.id)!!.status)
        }

    @Test
    fun `reapStale returns stuck processing rows to pending`() =
        runBlocking {
            repository.enqueue(listOf(newRow()))
            val claimed = repository.claimBatch(10).single()
            assertEquals(NotificationStatus.Processing, repository.getById(claimed.id)!!.status)

            // Treat anything claimed before "now + 1 min" as stale → reaps the row just claimed.
            val reaped = repository.reapStale(OffsetDateTime.now().plusMinutes(1))
            assertEquals(1, reaped)
            assertEquals(NotificationStatus.Pending, repository.getById(claimed.id)!!.status)

            // It is claimable again.
            assertTrue(repository.claimBatch(10).any { it.id == claimed.id })
        }
}
