package eu.kasify.db

import eu.kasify.TEST_ACCOUNT_1_PROPERTY_1
import eu.kasify.TEST_ACCOUNT_ID_1
import eu.kasify.db.model.SmartlockVendor
import eu.kasify.routes.BaseRouteDBTests
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Covers the admin properties list integration filters (`sesConfigured`,
 * `nukiConfigured`, `unifiConfigured`) and the smart-lock vendor batch lookup.
 *
 * Fixture: TEST_ACCOUNT_1_PROPERTY_1 has no integrations; `sesNukiProperty` has an
 * SES establishment code plus a Nuki lock; `unifiProperty` has a Unifi lock only.
 */
class PropertiesRepositoryAdminFiltersTest : BaseRouteDBTests() {
    private val repository = PropertiesRepository()
    private val smartlocksRepository = SmartlocksRepository()

    private val sesNukiPropertyId = Uuid.parse("30000000-0000-0000-0000-000000000101")
    private val unifiPropertyId = Uuid.parse("30000000-0000-0000-0000-000000000102")

    override fun setupData() {
        testDBConnection.insertProperty(
            id = sesNukiPropertyId,
            accountId = TEST_ACCOUNT_ID_1,
            name = "SES and Nuki property",
        )
        testDBConnection.insertProperty(
            id = unifiPropertyId,
            accountId = TEST_ACCOUNT_ID_1,
            name = "Unifi property",
        )

        testDBConnection.executeQuery(
            "UPDATE properties SET ses_establishment_code = '0000012345' WHERE id = '$sesNukiPropertyId'",
        )
        testDBConnection.executeQuery(
            "INSERT INTO property_smartlocks (property_id, vendor, name) " +
                "VALUES ('$sesNukiPropertyId', 'nuki', 'Front door')",
        )
        testDBConnection.executeQuery(
            "INSERT INTO property_smartlocks (property_id, vendor, name) " +
                "VALUES ('$unifiPropertyId', 'unifi', 'Main gate')",
        )
    }

    private fun listProperties(
        sesConfigured: Boolean? = null,
        nukiConfigured: Boolean? = null,
        unifiConfigured: Boolean? = null,
    ): Set<Uuid> =
        runBlocking {
            repository
                .getAllPropertiesWithOwner(
                    query = null,
                    pageable = null,
                    sesConfigured = sesConfigured,
                    nukiConfigured = nukiConfigured,
                    unifiConfigured = unifiConfigured,
                ).content
                .map { it.id }
                .toSet()
        }

    @Test
    fun testNoFiltersReturnsAllProperties() {
        assertEquals(
            setOf(TEST_ACCOUNT_1_PROPERTY_1.id, sesNukiPropertyId, unifiPropertyId),
            listProperties(),
        )
    }

    @Test
    fun testSesConfiguredFilter() {
        assertEquals(setOf(sesNukiPropertyId), listProperties(sesConfigured = true))
        assertEquals(
            setOf(TEST_ACCOUNT_1_PROPERTY_1.id, unifiPropertyId),
            listProperties(sesConfigured = false),
        )
    }

    @Test
    fun testNukiConfiguredFilter() {
        assertEquals(setOf(sesNukiPropertyId), listProperties(nukiConfigured = true))
        assertEquals(
            setOf(TEST_ACCOUNT_1_PROPERTY_1.id, unifiPropertyId),
            listProperties(nukiConfigured = false),
        )
    }

    @Test
    fun testUnifiConfiguredFilter() {
        assertEquals(setOf(unifiPropertyId), listProperties(unifiConfigured = true))
        assertEquals(
            setOf(TEST_ACCOUNT_1_PROPERTY_1.id, sesNukiPropertyId),
            listProperties(unifiConfigured = false),
        )
    }

    @Test
    fun testCombinedFilters() {
        assertEquals(
            setOf(sesNukiPropertyId),
            listProperties(sesConfigured = true, nukiConfigured = true),
        )
        assertEquals(
            emptySet(),
            listProperties(nukiConfigured = true, unifiConfigured = true),
        )
        assertEquals(
            setOf(TEST_ACCOUNT_1_PROPERTY_1.id),
            listProperties(sesConfigured = false, nukiConfigured = false, unifiConfigured = false),
        )
    }

    @Test
    fun testGetVendorsByPropertyIds() =
        runBlocking {
            val vendors =
                smartlocksRepository.getVendorsByPropertyIds(
                    listOf(TEST_ACCOUNT_1_PROPERTY_1.id, sesNukiPropertyId, unifiPropertyId),
                )

            assertEquals(setOf(SmartlockVendor.Nuki), vendors[sesNukiPropertyId])
            assertEquals(setOf(SmartlockVendor.Unifi), vendors[unifiPropertyId])
            assertEquals(null, vendors[TEST_ACCOUNT_1_PROPERTY_1.id])

            assertEquals(emptyMap(), smartlocksRepository.getVendorsByPropertyIds(emptyList()))
        }
}
