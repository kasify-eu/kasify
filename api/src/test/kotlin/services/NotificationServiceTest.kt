package eu.kasify.services

import eu.kasify.config.EmailConfig
import eu.kasify.db.NewNotification
import eu.kasify.db.NotificationsRepository
import eu.kasify.model.NotificationChannelType
import eu.kasify.model.NotificationPayload
import eu.kasify.model.NotificationType
import eu.kasify.model.email.Locale
import eu.kasify.services.notifications.ChannelResolver
import eu.kasify.services.notifications.NotificationService
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class NotificationServiceTest {
    private val notificationsRepository = mockk<NotificationsRepository>()
    private val channelResolver = mockk<ChannelResolver>()
    private val config =
        EmailConfig(
            enabled = false,
            fromAddress = "Kasify <test@example.com>",
            resendApiKey = "",
            dashboardBaseUrl = "https://app.example.com",
        )

    private fun service() = NotificationService(notificationsRepository, channelResolver, config)

    private val payload = NotificationPayload("invitation", Locale.EN, emptyMap())

    @Test
    fun `enqueueToUser writes one row per resolved channel`() =
        runBlocking {
            val userId = Uuid.random()
            val captured = slot<List<NewNotification>>()
            coEvery { channelResolver.channelsForUser(userId) } returns
                listOf(NotificationChannelType.Email, NotificationChannelType.Telegram)
            coEvery { notificationsRepository.enqueue(capture(captured)) } returns 2

            service().enqueueToUser(userId, NotificationType.MemberJoined, payload)

            val rows = captured.captured
            assertEquals(2, rows.size)
            assertEquals(
                listOf(NotificationChannelType.Email, NotificationChannelType.Telegram),
                rows.map { it.channel },
            )
            // All rows share one group and target the user (no raw email override).
            assertEquals(1, rows.map { it.groupId }.toSet().size)
            rows.forEach {
                assertEquals(userId, it.userId)
                assertNull(it.emailOverride)
                assertEquals(NotificationType.MemberJoined, it.eventType)
            }
        }

    @Test
    fun `enqueueToEmail writes a single email-only row with a raw address`() =
        runBlocking {
            val captured = slot<List<NewNotification>>()
            coEvery { channelResolver.channelsForEmailOnly() } returns listOf(NotificationChannelType.Email)
            coEvery { notificationsRepository.enqueue(capture(captured)) } returns 1

            service().enqueueToEmail("invitee@example.com", NotificationType.Invitation, payload)

            val rows = captured.captured
            assertEquals(1, rows.size)
            assertEquals(NotificationChannelType.Email, rows[0].channel)
            assertEquals("invitee@example.com", rows[0].emailOverride)
            assertNull(rows[0].userId)
        }

    @Test
    fun `enqueue failures are swallowed (best-effort)`() =
        runBlocking {
            val userId = Uuid.random()
            coEvery { channelResolver.channelsForUser(userId) } returns listOf(NotificationChannelType.Email)
            coEvery { notificationsRepository.enqueue(any()) } throws RuntimeException("db down")

            // Must not throw — a notification failure can never break the business operation.
            service().enqueueToUser(userId, NotificationType.MemberJoined, payload)
        }
}
