package eu.kasify

import kotlin.uuid.Uuid

class TestUser(
    val id: Uuid,
    val accountId: Uuid,
) {
    val username: String
        get() = id.toString().takeLast(5)
    val token: String
        get() = "token-$username"
}

data class TestProperty(
    val id: Uuid,
    val accountId: Uuid,
    val name: String,
    val tzone: String,
)

val TEST_ACCOUNT_ID_1 = Uuid.parse("00000000-0000-0000-0000-000000000001")
val TEST_ACCOUNT_ID_2 = Uuid.parse("00000000-0000-0000-0000-000000000002")

// Geo seed UUIDs shared across tests. Spain is always seeded by BaseRouteDBTests so any
// property insert can reference it; subclasses can opt-in additional countries via setupData.
val TEST_SPAIN_COUNTRY_ID = Uuid.parse("c0000000-0000-0000-0000-000000000001")
val TEST_USA_COUNTRY_ID = Uuid.parse("c0000000-0000-0000-0000-000000000002")
val TEST_ACCOUNT_1_USER_1 =
    TestUser(
        id = Uuid.parse("10000000-0000-0000-0000-000000000001"),
        accountId = TEST_ACCOUNT_ID_1,
    )

val TEST_ACCOUNT_1_USER_2 =
    TestUser(
        id = Uuid.parse("10000000-0000-0000-0000-000000000002"),
        accountId = TEST_ACCOUNT_ID_1,
    )

val TEST_ACCOUNT_2_USER_3 =
    TestUser(
        id = Uuid.parse("10000000-0000-0000-0000-000000000003"),
        accountId = TEST_ACCOUNT_ID_2,
    )

val TEST_ACCOUNT_1_PROPERTY_1 =
    TestProperty(
        id = Uuid.parse("30000000-0000-0000-0000-000000000001"),
        accountId = TEST_ACCOUNT_ID_1,
        name = "La casita del test",
        tzone = "Europe/Madrid",
    )

val properties: MutableList<TestProperty> =
    mutableListOf(
        TEST_ACCOUNT_1_PROPERTY_1,
    )

val users: MutableList<TestUser> =
    mutableListOf(
        TEST_ACCOUNT_1_USER_1,
        TEST_ACCOUNT_1_USER_2,
        TEST_ACCOUNT_2_USER_3,
    )
