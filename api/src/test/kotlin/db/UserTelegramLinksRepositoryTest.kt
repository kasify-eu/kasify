package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_USER_1
import eu.kasify.TEST_ACCOUNT_1_USER_2
import eu.kasify.model.TelegramLinkStatus
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserTelegramLinksRepositoryTest : BaseRouteDBTests() {
    private val repository = UserTelegramLinksRepository()
    private val userId = TEST_ACCOUNT_1_USER_1.id

    @Test
    fun `startLink creates a pending link discoverable by token`() =
        runBlocking {
            repository.startLink(userId, "tok-1", OffsetDateTime.now().plusMinutes(15))

            val link = repository.getByUserId(userId)!!
            assertEquals(TelegramLinkStatus.Pending, link.status)
            assertEquals("tok-1", link.linkToken)
            assertNull(repository.findChatId(userId))

            assertEquals(userId, repository.findByToken("tok-1")!!.userId)
        }

    @Test
    fun `startLink again invalidates the previous token (upsert on user)`() =
        runBlocking {
            repository.startLink(userId, "tok-old", OffsetDateTime.now().plusMinutes(15))
            repository.startLink(userId, "tok-new", OffsetDateTime.now().plusMinutes(15))

            assertNull(repository.findByToken("tok-old"))
            assertNotNull(repository.findByToken("tok-new"))
            // Still a single row for the user.
            assertEquals("tok-new", repository.getByUserId(userId)!!.linkToken)
        }

    @Test
    fun `markLinked stores the chat id and clears the token`() =
        runBlocking {
            repository.startLink(userId, "tok-1", OffsetDateTime.now().plusMinutes(15))
            repository.markLinked(userId, chatId = 555, username = "alice")

            val link = repository.getByUserId(userId)!!
            assertEquals(TelegramLinkStatus.Linked, link.status)
            assertEquals(555, link.chatId)
            assertEquals("alice", link.telegramUsername)
            assertNull(link.linkToken)
            assertEquals(555L, repository.findChatId(userId))
        }

    @Test
    fun `unlink revokes the link and forgets the chat id`() =
        runBlocking {
            repository.startLink(userId, "tok-1", OffsetDateTime.now().plusMinutes(15))
            repository.markLinked(userId, chatId = 777, username = null)

            assertTrue(repository.unlink(userId))

            val link = repository.getByUserId(userId)!!
            assertEquals(TelegramLinkStatus.Revoked, link.status)
            assertNull(link.chatId)
            assertNull(repository.findChatId(userId))
        }

    @Test
    fun `unlink returns false when there is no link`() =
        runBlocking {
            assertFalse(repository.unlink(TEST_ACCOUNT_1_USER_2.id))
        }
}
