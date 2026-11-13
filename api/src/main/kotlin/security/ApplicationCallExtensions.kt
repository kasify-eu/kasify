package eu.kasify.security

import eu.kasify.routes.exceptions.NotAuthorizedException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

fun ApplicationCall.getKasifyUser(): KasifyUserPrincipal = principal<KasifyUserPrincipal>() ?: throw NotAuthorizedException("User not found")

fun KasifyUserPrincipal.isAdmin(): Boolean = roles.any { it.key == "admin" }
