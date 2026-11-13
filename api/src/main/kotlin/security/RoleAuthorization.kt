package eu.kasify.security

import eu.kasify.routes.exceptions.ForbiddenException
import eu.kasify.routes.exceptions.NotAuthorizedException
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingResolveContext

fun Route.requireRole(
    role: String,
    build: Route.() -> Unit,
): Route {
    val authorizedRoute = createChild(RoleRouteSelector(role)) as RoutingNode
    authorizedRoute.intercept(ApplicationCallPipeline.Call) {
        val principal =
            context.principal<KasifyUserPrincipal>()
                ?: throw NotAuthorizedException("User not found")

        if (principal.roles.none { it.key == role }) {
            throw ForbiddenException("Role '$role' required")
        }
    }
    authorizedRoute.build()
    return authorizedRoute
}

private class RoleRouteSelector(
    private val role: String,
) : RouteSelector() {
    override suspend fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int,
    ): RouteSelectorEvaluation = RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(requireRole: $role)"
}
