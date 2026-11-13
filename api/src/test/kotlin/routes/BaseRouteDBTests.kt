package eu.kasify.routes
import eu.kasify.TEST_SPAIN_COUNTRY_ID
import eu.kasify.TestProperty
import eu.kasify.extensions.TEST_ENCRYPTION_KEY
import eu.kasify.extensions.configureTestModules
import eu.kasify.properties
import eu.kasify.security.EncryptionService
import eu.kasify.users
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.Before
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

open class BaseRouteDBTests {
    private val postgresqlUser = "postgres"
    private val postgresqlPassword = "postgres"

    fun Connection.executeQuery(query: String) {
        this.createStatement().use { stmt ->
            // Drop database if exists
            stmt.execute(query)
        }
    }

    fun Connection.insertProperty(
        id: kotlin.uuid.Uuid,
        accountId: kotlin.uuid.Uuid,
        name: String,
        tzone: String = "Europe/Madrid",
        address: String = "Test Address",
        city: String = "Test City",
        zipcode: Int = 29730,
        countryId: kotlin.uuid.Uuid = TEST_SPAIN_COUNTRY_ID,
        createdById: kotlin.uuid.Uuid? = null,
    ): TestProperty {
        val ownerId = createdById ?: users.first { u -> u.accountId == accountId }.id
        executeQuery(
            "INSERT INTO properties (id, account_id, name, tzone, address, city, zipcode, country_id, created_by_id, default_cleaner_id) " +
                "VALUES('$id', '$accountId', '$name', '$tzone', '$address', '$city', $zipcode, '$countryId', '$ownerId', '$ownerId')",
        )
        return TestProperty(id = id, accountId = accountId, name = name, tzone = tzone)
    }

    /**
     * Seeds an enabled SES Hospedajes integration (account_integrations + the vendor row) for
     * [accountId] and returns the SES integration id. The password is encrypted with the test key,
     * so repository reads that decrypt it (e.g. `getSesHospedajesIntegrationById`) work.
     */
    fun Connection.insertSesIntegration(
        accountId: kotlin.uuid.Uuid,
        integrationId: kotlin.uuid.Uuid = kotlin.uuid.Uuid.random(),
        accountIntegrationId: kotlin.uuid.Uuid = kotlin.uuid.Uuid.random(),
        arrendadorCode: String = "0000145750",
        enabled: Boolean = true,
    ): kotlin.uuid.Uuid {
        val encryptedPassword = EncryptionService(key = TEST_ENCRYPTION_KEY).encrypt("ses-pass")
        executeQuery(
            "INSERT INTO account_integrations (id, account_id, vendor) " +
                "VALUES ('$accountIntegrationId', '$accountId', 'seshospedajes')",
        )
        executeQuery(
            "INSERT INTO account_ses_hospedajes_integrations " +
                "(id, account_integration_id, arrendador_code, ses_username, ses_password, enabled) " +
                "VALUES ('$integrationId', '$accountIntegrationId', '$arrendadorCode', 'ses-user', '$encryptedPassword', $enabled)",
        )
        return integrationId
    }

    /** Assigns a property to a SES integration (strict participation model) with a code. */
    fun Connection.assignPropertyToSesIntegration(
        propertyId: kotlin.uuid.Uuid,
        integrationId: kotlin.uuid.Uuid,
        establishmentCode: String = "EST-001",
    ) {
        executeQuery(
            "UPDATE properties SET ses_establishment_code = '$establishmentCode', " +
                "ses_integration_id = '$integrationId' WHERE id = '$propertyId'",
        )
    }

    lateinit var testDBConnection: Connection

    @Before
    fun setup() {
        // First connect to a postgres database to drop/create kasify_tests
        val adminConnection =
            DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/postgres",
                postgresqlUser,
                postgresqlPassword,
            )

        adminConnection.executeQuery("DROP DATABASE IF EXISTS kasify_tests WITH (FORCE)")
        adminConnection.executeQuery("CREATE DATABASE kasify_tests")

        // Create the test DB
        testDBConnection =
            DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/kasify_tests",
                postgresqlUser,
                postgresqlPassword,
            )

        val dbScript = File("db.sql").readText()
        testDBConnection.executeQuery(dbScript)

        // Extract unique account IDs from users
        val uniqueAccountIds = users.map { it.accountId }.toSet()

        // Insert accounts first
        uniqueAccountIds.forEach { accountId ->
            testDBConnection.executeQuery("INSERT INTO accounts (id) VALUES ('$accountId')")
        }

        // Insert test users
        users.forEach {
            testDBConnection.executeQuery(
                "INSERT INTO users (id, account_id, name, last_name, email, external_user_id) VALUES ('${it.id}', '${it.accountId}', '${it.username}', 'last name', '${it.username}@email.com', 'ex-${it.id}')",
            )
        }

        // Seed Spain so properties.country_id (NOT NULL FK) has something to reference.
        // Subclasses can add more countries (e.g. USA) in setupData without conflict.
        testDBConnection.executeQuery(
            "INSERT INTO countries (id, name, name_es, alpha_2, alpha_3) VALUES " +
                "('$TEST_SPAIN_COUNTRY_ID', 'Spain', 'España', 'ES', 'ESP')",
        )

        properties.forEach {
            val ownerId = users.first { u -> u.accountId == it.accountId }.id
            testDBConnection.executeQuery(
                "INSERT INTO properties (id, account_id, name, tzone, address, city, zipcode, country_id, created_by_id, default_cleaner_id) " +
                    "VALUES ('${it.id}', '${it.accountId}', '${it.name}', '${it.tzone}', 'address', 'city', 29730, '$TEST_SPAIN_COUNTRY_ID', '$ownerId', '$ownerId')",
            )
        }

        setupData()
        connectKasifyTestsDB()
    }

    open fun setupData() {}

    fun connectKasifyTestsDB() {
        Database.Companion.connect(
            url = "jdbc:postgresql://localhost:5432/kasify_tests",
            user = postgresqlUser,
            password = postgresqlPassword,
        )
    }

    @Test
    fun testRoot() =
        testApplication {
            application { configureTestModules() }

            client.get("/").apply {
                assertEquals(HttpStatusCode.Companion.OK, status)
            }
        }

    @Test
    fun testRoot2() =
        testApplication {
            application { configureTestModules() }

            client.get("/").apply {
                assertEquals(HttpStatusCode.Companion.OK, status)
            }
        }
}
