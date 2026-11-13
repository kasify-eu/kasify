package eu.kasify.jobs

import eu.kasify.db.NotificationsRepository
import eu.kasify.model.Notification
import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.NotificationStatus
import eu.kasify.model.NotificationType
import eu.kasify.model.email.Locale
import eu.kasify.services.notifications.NotificationChannel
import eu.kasify.services.notifications.NotificationDeliveryException
import eu.kasify.services.notifications.NotificationRecipient
import eu.kasify.services.notifications.RecipientResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.uuid.Uuid

class NotificationDispatchJobTest {
    private val notificationsRepository = mockk<NotificationsRepository>(relaxed = true)
    private val recipientResolver = mockk<RecipientResolver>()
    private val emailChannel = mockk<NotificationChannel>()

    private fun job() =
        NotificationDispatchJob(
            notificationsRepository = notificationsRepository,
            recipientResolver = recipientResolver,
            channels = mapOf(NotificationChannelType.Email to emailChannel),
        )

    private fun notification(
        channel: NotificationChannelType = NotificationChannelType.Email,
        attemptCount: Int = 0,
        maxAttempts: Int = 5,
    ): Notification {
        val now = OffsetDateTime.now()
        return Notification(
            id = Uuid.random(),
            groupId = Uuid.random(),
            userId = null,
            emailOverride = "to@example.com",
            eventType = NotificationType.Invitation,
            channel = channel,
            payload = NotificationPayload("invitation", Locale.EN, emptyMap()),
            status = NotificationStatus.Pending,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            nextAttemptAt = now,
            claimedAt = now,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `successful delivery marks the notification sent`() =
        runBlocking {
            val n = notification()
            coEvery { notificationsRepository.reapStale(any()) } returns 0
            coEvery { notificationsRepository.claimBatch(any()) } returns listOf(n)
            coEvery { recipientResolver.resolve(n) } returns NotificationRecipient("to@example.com", null)
            coEvery { emailChannel.send(any(), any()) } returns "provider-1"

            job().runOnce()

            coVerify(exactly = 1) { notificationsRepository.markSent(n.id, 1) }
        }

    @Test
    fun `a retryable failure reschedules with an incremented attempt`() =
        runBlocking {
            val n = notification(attemptCount = 0, maxAttempts = 5)
            coEvery { notificationsRepository.reapStale(any()) } returns 0
            coEvery { notificationsRepository.claimBatch(any()) } returns listOf(n)
            coEvery { recipientResolver.resolve(n) } returns NotificationRecipient("to@example.com", null)
            coEvery { emailChannel.send(any(), any()) } throws NotificationDeliveryException("smtp down", retryable = true)

            job().runOnce()

            coVerify(exactly = 1) { notificationsRepository.markRetry(n.id, 1, any(), any()) }
            coVerify(exactly = 0) { notificationsRepository.markDead(any(), any(), any()) }
        }

    @Test
    fun `a non-retryable failure dead-letters immediately`() =
        runBlocking {
            val n = notification(attemptCount = 0, maxAttempts = 5)
            coEvery { notificationsRepository.reapStale(any()) } returns 0
            coEvery { notificationsRepository.claimBatch(any()) } returns listOf(n)
            coEvery { recipientResolver.resolve(n) } returns NotificationRecipient("to@example.com", null)
            coEvery { emailChannel.send(any(), any()) } throws NotificationDeliveryException("bad address", retryable = false)

            job().runOnce()

            coVerify(exactly = 1) { notificationsRepository.markDead(n.id, 1, any()) }
        }

    @Test
    fun `exhausting attempts dead-letters`() =
        runBlocking {
            val n = notification(attemptCount = 4, maxAttempts = 5)
            coEvery { notificationsRepository.reapStale(any()) } returns 0
            coEvery { notificationsRepository.claimBatch(any()) } returns listOf(n)
            coEvery { recipientResolver.resolve(n) } returns NotificationRecipient("to@example.com", null)
            coEvery { emailChannel.send(any(), any()) } throws NotificationDeliveryException("still down", retryable = true)

            job().runOnce()

            // attempt becomes 5 == maxAttempts → dead, not retried.
            coVerify(exactly = 1) { notificationsRepository.markDead(n.id, 5, any()) }
            coVerify(exactly = 0) { notificationsRepository.markRetry(any(), any(), any(), any()) }
        }

    @Test
    fun `a row for an unconfigured channel is dead-lettered`() =
        runBlocking {
            val n = notification(channel = NotificationChannelType.Telegram)
            coEvery { notificationsRepository.reapStale(any()) } returns 0
            coEvery { notificationsRepository.claimBatch(any()) } returns listOf(n)

            job().runOnce()

            coVerify(exactly = 1) { notificationsRepository.markDead(n.id, 1, any()) }
        }
}
