package eu.kasify.routes

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvitationEmailValidationTest {
    // The pattern the route used before the ReDoS fix. Kept here only as the oracle the new
    // pattern must agree with; never run it against the attack input (it is O(N^2)).
    private val legacyRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    private fun legacyMatches(input: String) = legacyRegex.matches(input)

    @Test
    fun `accepts the same representative addresses as the legacy pattern`() {
        val accepted =
            listOf(
                "member@example.com",
                "a@b.c",
                "first.last+tag@sub.example.co.uk",
                "a@.b.c",
                "a@..b",
                "a@b..c",
                "a@b.c.",
                "a@b...",
                "a@...",
                "a@b.c.d.e",
                ".@a.b",
                "a.@b.c",
                "a@b. ",
                "ñ@dominio.es",
                "😀@😀.😀",
            )
        for (email in accepted) {
            assertTrue(legacyMatches(email), "legacy pattern should accept '$email'")
            assertTrue(isValidInvitationEmail(email), "new pattern should accept '$email'")
        }
    }

    @Test
    fun `rejects the same representative addresses as the legacy pattern`() {
        val rejected =
            listOf(
                "",
                "@",
                "a@",
                "@b.c",
                "a@b",
                "a@b.",
                "a@.b",
                "a@.",
                "a@..",
                "a.b.c",
                "a@b@c.d",
                "a@b.c@",
                "a b@c.d",
                "a@b c.d",
                "a@b.c ",
                " a@b.c",
                "a@b.c\n",
                "a@b.c\r\n",
                "a\t@b.c",
                "a@b\u000b.c",
                "a@b.\u000c",
                "a@" + ".".repeat(10) + "@",
            )
        for (email in rejected) {
            assertFalse(legacyMatches(email), "legacy pattern should reject '$email'")
            assertFalse(isValidInvitationEmail(email), "new pattern should reject '$email'")
        }
    }

    @Test
    fun `agrees with the legacy pattern on every short string over the structural alphabet`() {
        assertEquivalentUpTo(alphabet = "a.@", maxLength = 10)
    }

    @Test
    fun `agrees with the legacy pattern on every short string including whitespace variants`() {
        assertEquivalentUpTo(alphabet = "a.@ \t\n\u000b\u000c\r ", maxLength = 5)
    }

    @Test(timeout = 5_000)
    fun `rejects the quadratic backtracking attack string in linear time`() {
        val attack = "a@" + ".".repeat(1_000_000) + "@"
        assertFalse(isValidInvitationEmail(attack))
    }

    @Test(timeout = 5_000)
    fun `accepts a very long valid address in linear time`() {
        val longValid = "a".repeat(500_000) + "@" + "b".repeat(500_000) + ".com"
        assertTrue(isValidInvitationEmail(longValid))
    }

    private fun assertEquivalentUpTo(
        alphabet: String,
        maxLength: Int,
    ) {
        var checked = 0
        val buffer = CharArray(maxLength)

        fun visit(length: Int) {
            val candidate = String(buffer, 0, length)
            assertEquals(
                legacyMatches(candidate),
                isValidInvitationEmail(candidate),
                "patterns disagree on '${candidate.escapeForMessage()}'",
            )
            checked++
            if (length == maxLength) return
            for (c in alphabet) {
                buffer[length] = c
                visit(length + 1)
            }
        }

        visit(0)
        var expected = 0
        var power = 1
        for (i in 0..maxLength) {
            expected += power
            power *= alphabet.length
        }
        assertEquals(expected, checked)
    }

    private fun String.escapeForMessage(): String =
        buildString {
            for (c in this@escapeForMessage) {
                if (c.code < 0x20 || c.code == 0xa0) append("\\u%04x".format(c.code)) else append(c)
            }
        }
}
