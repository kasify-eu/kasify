package eu.kasify.routes

import eu.kasify.authenticatedAndRateLimited
import eu.kasify.db.UsersRepository
import eu.kasify.model.api.Account
import eu.kasify.model.api.UpdateAccountRequest
import eu.kasify.routes.exceptions.NotFoundException
import eu.kasify.security.getKasifyUser
import eu.kasify.versionedRouting
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.put
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.getValue

@OptIn(ExperimentalKtorApi::class)
fun Application.configureAccountRouting() {
    val usersRepository by inject<UsersRepository>()

    versionedRouting {
        authenticatedAndRateLimited {
            get("/account") {
                val user = call.getKasifyUser()
                val u = usersRepository.getUserById(user.userId) ?: throw NotFoundException("User not found")

                call.respond(HttpStatusCode.OK, Account.from(u))
            }.describe {
                tag("Account")
                summary = "Get account"
                responses {
                    HttpStatusCode.OK {
                        description = "The current user's account"
                        schema = jsonSchema<Account>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                    HttpStatusCode.NotFound { description = "The authenticated user has no account record" }
                }
            }

            put("/account") {
                val user = call.getKasifyUser()
                usersRepository.getUserById(user.userId) ?: throw NotFoundException("User not found")

                val request = call.receive<UpdateAccountRequest>()
                usersRepository.updateUser(user.userId, request.name, request.lastName, request.locale)

                val updated = usersRepository.getUserById(user.userId) ?: throw NotFoundException("User not found")
                call.respond(HttpStatusCode.OK, Account.from(updated))
            }.describe {
                tag("Account")
                summary = "Update account"
                requestBody {
                    description = "New name and last name for the account, plus an optional preferred language (en/es)"
                    required = true
                    schema = jsonSchema<UpdateAccountRequest>()
                }
                responses {
                    HttpStatusCode.OK {
                        description = "The updated account"
                        schema = jsonSchema<Account>()
                    }
                    HttpStatusCode.Unauthorized { description = "The request is not authenticated" }
                    HttpStatusCode.NotFound { description = "The authenticated user has no account record" }
                }
            }
        }
    }
}
