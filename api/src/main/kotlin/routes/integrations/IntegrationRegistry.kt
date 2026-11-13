package eu.kasify.routes.integrations

class IntegrationRegistry {
    private val handlers = mutableMapOf<String, IntegrationHandler>()

    fun register(handler: IntegrationHandler) {
        handlers[handler.pathSegment] = handler
    }

    fun get(pathSegment: String): IntegrationHandler? = handlers[pathSegment]

    fun all(): Collection<IntegrationHandler> = handlers.values
}
