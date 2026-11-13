package eu.kasify.plugins

import eu.kasify.db.IntegrationsRepository
import eu.kasify.db.model.SmartlockVendor
import eu.kasify.plugins.nuki.NukiPlugin
import eu.kasify.plugins.nuki.NukiSmartLockPlugin
import eu.kasify.plugins.unifi.UnifiSmartLockPlugin
import eu.kasify.services.UnifiSessionManager
import kotlin.uuid.Uuid

open class PluginFactory(
    private val integrationsRepository: IntegrationsRepository?,
    private val unifiSessionManager: UnifiSessionManager?,
) {
    open suspend fun getSmartLockPlugin(
        accountId: Uuid,
        vendor: SmartlockVendor,
    ): SmartLockPlugin {
        requireNotNull(integrationsRepository) { "IntegrationsRepository is required" }
        return when (vendor) {
            SmartlockVendor.Unifi -> getUnifiPlugin(accountId)
            SmartlockVendor.Nuki -> getNukiPlugin(accountId)
            else -> {
                throw Exception()
            }
        }
    }

    open suspend fun getNukiPlugin(accountId: Uuid): NukiSmartLockPlugin {
        requireNotNull(integrationsRepository) { "IntegrationsRepository is required" }
        val config =
            integrationsRepository.getNukiIntegrationsByAccountId(accountId).firstOrNull()
                ?: throw Exception("Nuki integration not found for account $accountId")
        return NukiSmartLockPlugin(config.nukiToken)
    }

    open suspend fun getNukiPluginByIntegrationId(integrationId: Uuid): NukiSmartLockPlugin {
        requireNotNull(integrationsRepository) { "IntegrationsRepository is required" }
        val config =
            integrationsRepository.getNukiIntegrationByIntegrationId(integrationId)
                ?: throw Exception("Nuki integration not found")
        return NukiSmartLockPlugin(config.nukiToken)
    }

    /**
     * Creates a NukiPlugin instance from a token.
     * Used for validating tokens and fetching smartlocks.
     */
    open fun createNukiPlugin(nukiToken: String): NukiPlugin = NukiPlugin(nukiToken)

    open suspend fun getUnifiPlugin(accountId: Uuid): UnifiSmartLockPlugin {
        requireNotNull(unifiSessionManager) { "UnifiSessionManager is required" }
        return UnifiSmartLockPlugin(accountId, unifiSessionManager)
    }
}
