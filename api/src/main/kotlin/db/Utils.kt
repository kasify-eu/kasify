package eu.kasify.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

suspend fun <T> dbQuery(block: suspend () -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }

/**
 * Escapes the LIKE wildcards (`%`, `_`) and the escape character itself so user
 * input matches literally when embedded in a LIKE pattern.
 */
fun escapeLike(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
