package eu.kasify.mocks

import eu.kasify.db.model.SmartlockVendor
import eu.kasify.model.BookingAccessCode
import eu.kasify.model.PropertySmartlock
import eu.kasify.plugins.PluginFactory
import eu.kasify.plugins.SmartLockPlugin
import eu.kasify.plugins.nuki.NukiPlugin
import eu.kasify.plugins.nuki.model.NukiConfig
import eu.kasify.plugins.nuki.model.NukiLockState
import eu.kasify.plugins.nuki.model.NukiSmartLock
import java.time.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Mock PluginFactory for testing that returns mock smart lock plugins
 * Avoids calling real external APIs during tests
 */
class MockPluginFactory : PluginFactory(null, null) {
    override suspend fun getSmartLockPlugin(
        accountId: Uuid,
        vendor: SmartlockVendor,
    ): SmartLockPlugin =
        when (vendor) {
            SmartlockVendor.Yale -> throw RuntimeException("Yale integration not configured")
            else -> MockSmartLockPlugin()
        }

    override suspend fun getNukiPlugin(accountId: Uuid): eu.kasify.plugins.nuki.NukiSmartLockPlugin = throw NotImplementedError("Use getSmartLockPlugin instead")

    override suspend fun getNukiPluginByIntegrationId(integrationId: Uuid): eu.kasify.plugins.nuki.NukiSmartLockPlugin = MockNukiSmartLockPlugin()

    override suspend fun getUnifiPlugin(accountId: Uuid): eu.kasify.plugins.unifi.UnifiSmartLockPlugin = throw NotImplementedError("Use getSmartLockPlugin instead")

    override fun createNukiPlugin(nukiToken: String): NukiPlugin = MockNukiPlugin()
}

/**
 * Mock NukiSmartLockPlugin for testing
 * Avoids making real Nuki API calls
 */
class MockNukiSmartLockPlugin : eu.kasify.plugins.nuki.NukiSmartLockPlugin("mock-token") {
    override suspend fun createAccessCode(
        guestFirstName: String,
        guestLastName: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
        pinCode: String,
        smartLock: PropertySmartlock,
    ): String? = "MOCK_NUKI_VENDOR_CODE_${System.currentTimeMillis()}"

    override suspend fun deleteAccessCode(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
    ) {
        // Do nothing - mock deletion
    }

    override suspend fun updateAccessCodeDates(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
        from: LocalDateTime,
        to: LocalDateTime,
    ) {
        // Do nothing - mock update
    }
}

/**
 * Mock NukiPlugin for testing
 * Returns predictable test data without making external API calls
 */
class MockNukiPlugin : NukiPlugin("mock-token") {
    override suspend fun getSmartLocks(): List<NukiSmartLock> =
        listOf(
            NukiSmartLock(
                smartlockId = 123456789,
                accountId = 1,
                type = 0,
                authId = 1,
                name = "Mock Front Door",
                state = NukiLockState(state = 1, batteryCritical = false, batteryCharge = 85),
                config = NukiConfig(name = "Mock Front Door"),
                serverState = 0,
            ),
        )
}

/**
 * Mock SmartLockPlugin implementation for testing
 * Returns predictable test data without making external API calls
 */
class MockSmartLockPlugin : SmartLockPlugin {
    override suspend fun createAccessCode(
        guestFirstName: String,
        guestLastName: String,
        startTimeUTC: LocalDateTime,
        endTimeUTC: LocalDateTime,
        pinCode: String,
        smartLock: PropertySmartlock,
    ): String {
        // Return a mock vendor code ID
        return "MOCK_VENDOR_CODE_${System.currentTimeMillis()}"
    }

    override suspend fun deleteAccessCode(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
    ) {
        // Do nothing - mock deletion
    }

    override suspend fun updateAccessCodeDates(
        accessCode: BookingAccessCode,
        smartLock: PropertySmartlock,
        from: LocalDateTime,
        to: LocalDateTime,
    ) {
        // Do nothing - mock update
    }
}
