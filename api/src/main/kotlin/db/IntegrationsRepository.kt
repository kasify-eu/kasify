package eu.kasify.db
import eu.kasify.db.model.AccountIntegrationsTable
import eu.kasify.db.model.AccountNukiIntegrationsTable
import eu.kasify.db.model.AccountSesHospedajesIntegrationsTable
import eu.kasify.db.model.AccountUnifiIntegrationsTable
import eu.kasify.db.model.AccountsTable
import eu.kasify.db.model.IntegrationVendor
import eu.kasify.db.model.PropertyNukiSmartlocksTable
import eu.kasify.db.model.PropertySmartlocksTable
import eu.kasify.model.NukiIntegration
import eu.kasify.model.SesHospedajesIntegration
import eu.kasify.model.SesIntegrationGateState
import eu.kasify.model.UnifiIntegration
import eu.kasify.security.EncryptionService
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

open class IntegrationsRepository(
    val propertiesRepository: PropertiesRepository,
    private val encryptionService: EncryptionService,
) {
    private val madridZone = TimeZone.Companion.of("Europe/Madrid")

    open suspend fun getNukiIntegrationsByAccountId(accountId: Uuid): List<NukiIntegration> =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountNukiIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountNukiIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .selectAll()
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountIntegrationsTable.vendor eq IntegrationVendor.Nuki) and
                        (AccountsTable.deletedAt.isNull())
                }.map { NukiIntegration.from(it, encryptionService) }
        }

    open suspend fun getNukiIntegrationById(
        accountId: Uuid,
        integrationId: Uuid,
    ): NukiIntegration? =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountNukiIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountNukiIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .selectAll()
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountNukiIntegrationsTable.id eq integrationId) and
                        (AccountsTable.deletedAt.isNull())
                }.singleOrNull()
                ?.let { NukiIntegration.from(it, encryptionService) }
        }

    open suspend fun getNukiIntegrationByIntegrationId(integrationId: Uuid): NukiIntegration? =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountNukiIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountNukiIntegrationsTable.accountIntegrationId,
                ).selectAll()
                .where { AccountNukiIntegrationsTable.id eq integrationId }
                .singleOrNull()
                ?.let { NukiIntegration.from(it, encryptionService) }
        }

    open suspend fun createNukiIntegration(
        accountId: Uuid,
        nukiToken: String,
    ): NukiIntegration =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            // First, create the generic integration entry
            val accountIntegrationId =
                AccountIntegrationsTable.insert {
                    it[AccountIntegrationsTable.accountId] = accountId
                    it[AccountIntegrationsTable.vendor] = IntegrationVendor.Nuki
                    it[AccountIntegrationsTable.createdAt] = currentDateTime
                    it[AccountIntegrationsTable.updatedAt] = currentDateTime
                }[AccountIntegrationsTable.id]

            // Then, create the Nuki-specific integration entry
            val newIntegrationId =
                AccountNukiIntegrationsTable.insert {
                    it[AccountNukiIntegrationsTable.accountIntegrationId] = accountIntegrationId
                    it[AccountNukiIntegrationsTable.nukiToken] = encryptionService.encrypt(nukiToken)
                    it[AccountNukiIntegrationsTable.createdAt] = currentDateTime
                    it[AccountNukiIntegrationsTable.updatedAt] = currentDateTime
                }[AccountNukiIntegrationsTable.id]

            NukiIntegration(
                id = newIntegrationId,
                accountIntegrationId = accountIntegrationId,
                accountId = accountId,
                nukiToken = nukiToken,
                createdAt = currentDateTime.toLocalDateTime(),
                updatedAt = currentDateTime.toLocalDateTime(),
            )
        }

    open suspend fun updateNukiIntegration(
        accountId: Uuid,
        integrationId: Uuid,
        nukiToken: String,
    ): Boolean {
        return dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            // Verify the integration belongs to this account
            val exists =
                AccountIntegrationsTable
                    .join(
                        AccountNukiIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountNukiIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountNukiIntegrationsTable.id eq integrationId)
                    }.singleOrNull() ?: return@dbQuery false

            val accountIntegrationId = exists[AccountNukiIntegrationsTable.accountIntegrationId]

            AccountNukiIntegrationsTable.update(
                { AccountNukiIntegrationsTable.accountIntegrationId eq accountIntegrationId },
            ) {
                it[AccountNukiIntegrationsTable.nukiToken] = encryptionService.encrypt(nukiToken)
                it[AccountNukiIntegrationsTable.updatedAt] = currentDateTime
            } > 0
        }
    }

    open suspend fun deleteNukiIntegration(
        accountId: Uuid,
        integrationId: Uuid,
    ): Boolean =
        dbQuery {
            // Verify the integration belongs to this account and get the account integration ID
            val accountIntegrationId =
                AccountIntegrationsTable
                    .join(
                        AccountNukiIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountNukiIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountNukiIntegrationsTable.id eq integrationId)
                    }.singleOrNull()
                    ?.get(AccountNukiIntegrationsTable.accountIntegrationId) ?: return@dbQuery false

            // Delete all property smartlocks linked to this nuki integration;
            // cascade will handle the property_nuki_smartlocks vendor table
            val smartlockIds =
                PropertyNukiSmartlocksTable
                    .selectAll()
                    .where { PropertyNukiSmartlocksTable.nukiIntegrationId eq integrationId }
                    .map { it[PropertyNukiSmartlocksTable.propertySmartlockId] }

            if (smartlockIds.isNotEmpty()) {
                PropertySmartlocksTable.deleteWhere {
                    PropertySmartlocksTable.id inList smartlockIds
                }
            }

            // Delete from base table, cascade will handle vendor-specific table
            AccountIntegrationsTable.deleteWhere {
                AccountIntegrationsTable.id eq accountIntegrationId
            } > 0
        }

    open suspend fun getUnifiIntegrationsByAccountId(accountId: Uuid): List<UnifiIntegration> =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountUnifiIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountUnifiIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .selectAll()
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountIntegrationsTable.vendor eq IntegrationVendor.Unifi) and
                        (AccountsTable.deletedAt.isNull())
                }.map { UnifiIntegration.from(it, encryptionService) }
        }

    open suspend fun getUnifiIntegrationById(
        accountId: Uuid,
        integrationId: Uuid,
    ): UnifiIntegration? =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountUnifiIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountUnifiIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .selectAll()
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountUnifiIntegrationsTable.id eq integrationId) and
                        (AccountsTable.deletedAt.isNull())
                }.singleOrNull()
                ?.let { UnifiIntegration.from(it, encryptionService) }
        }

    open suspend fun createUnifiIntegration(
        accountId: Uuid,
        unifiConsoleUrl: String,
        unifiUsername: String,
        unifiPassword: String,
    ): UnifiIntegration =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            // First, create the generic integration entry
            val accountIntegrationId =
                AccountIntegrationsTable.insert {
                    it[AccountIntegrationsTable.accountId] = accountId
                    it[AccountIntegrationsTable.vendor] = IntegrationVendor.Unifi
                    it[AccountIntegrationsTable.createdAt] = currentDateTime
                    it[AccountIntegrationsTable.updatedAt] = currentDateTime
                }[AccountIntegrationsTable.id]

            // Then, create the Unifi-specific integration entry
            val newIntegrationId =
                AccountUnifiIntegrationsTable.insert {
                    it[AccountUnifiIntegrationsTable.accountIntegrationId] = accountIntegrationId
                    it[AccountUnifiIntegrationsTable.unifiConsoleUrl] = unifiConsoleUrl
                    it[AccountUnifiIntegrationsTable.unifiUsername] = unifiUsername
                    it[AccountUnifiIntegrationsTable.unifiPassword] = encryptionService.encrypt(unifiPassword)
                    it[AccountUnifiIntegrationsTable.createdAt] = currentDateTime
                    it[AccountUnifiIntegrationsTable.updatedAt] = currentDateTime
                }[AccountUnifiIntegrationsTable.id]

            UnifiIntegration(
                id = newIntegrationId,
                accountIntegrationId = accountIntegrationId,
                accountId = accountId,
                unifiConsoleUrl = unifiConsoleUrl,
                unifiUsername = unifiUsername,
                unifiPassword = unifiPassword,
                createdAt = currentDateTime.toLocalDateTime(),
                updatedAt = currentDateTime.toLocalDateTime(),
            )
        }

    open suspend fun updateUnifiIntegration(
        accountId: Uuid,
        integrationId: Uuid,
        unifiConsoleUrl: String,
        unifiUsername: String,
        unifiPassword: String,
    ): Boolean {
        return dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            // Verify the integration belongs to this account
            val exists =
                AccountIntegrationsTable
                    .join(
                        AccountUnifiIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountUnifiIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountUnifiIntegrationsTable.id eq integrationId)
                    }.singleOrNull() ?: return@dbQuery false

            val accountIntegrationId = exists[AccountUnifiIntegrationsTable.accountIntegrationId]

            // Update the Unifi-specific integration
            AccountUnifiIntegrationsTable.update(
                { AccountUnifiIntegrationsTable.accountIntegrationId eq accountIntegrationId },
            ) {
                it[AccountUnifiIntegrationsTable.unifiConsoleUrl] = unifiConsoleUrl
                it[AccountUnifiIntegrationsTable.unifiUsername] = unifiUsername
                it[AccountUnifiIntegrationsTable.unifiPassword] = encryptionService.encrypt(unifiPassword)
                it[AccountUnifiIntegrationsTable.updatedAt] = currentDateTime
            } > 0
        }
    }

    open suspend fun deleteUnifiIntegration(
        accountId: Uuid,
        integrationId: Uuid,
    ): Boolean =
        dbQuery {
            // Verify the integration belongs to this account and get the account integration ID
            val accountIntegrationId =
                AccountIntegrationsTable
                    .join(
                        AccountUnifiIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountUnifiIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountUnifiIntegrationsTable.id eq integrationId)
                    }.singleOrNull()
                    ?.get(AccountUnifiIntegrationsTable.accountIntegrationId) ?: return@dbQuery false

            // Delete from base table, cascade will handle vendor-specific table
            AccountIntegrationsTable.deleteWhere {
                AccountIntegrationsTable.id eq accountIntegrationId
            } > 0
        }

    open suspend fun updateUnifiSession(
        accountId: Uuid,
        csrfToken: String,
        cookies: String,
        expiresAt: java.time.OffsetDateTime,
    ): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            // Find the account integration ID for Unifi
            val accountIntegrationId =
                AccountIntegrationsTable
                    .selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountIntegrationsTable.vendor eq IntegrationVendor.Unifi)
                    }.singleOrNull()
                    ?.get(AccountIntegrationsTable.id) ?: return@dbQuery false

            AccountUnifiIntegrationsTable.update(
                { AccountUnifiIntegrationsTable.accountIntegrationId eq accountIntegrationId },
            ) {
                it[sessionCsrfToken] = csrfToken
                it[sessionCookies] = cookies
                it[sessionExpiresAt] = expiresAt
                it[updatedAt] = currentDateTime
            } > 0
        }

    open suspend fun clearUnifiSession(accountId: Uuid): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            // Find the account integration ID for Unifi
            val accountIntegrationId =
                AccountIntegrationsTable
                    .selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountIntegrationsTable.vendor eq IntegrationVendor.Unifi)
                    }.singleOrNull()
                    ?.get(AccountIntegrationsTable.id) ?: return@dbQuery false

            AccountUnifiIntegrationsTable.update(
                { AccountUnifiIntegrationsTable.accountIntegrationId eq accountIntegrationId },
            ) {
                it[sessionCsrfToken] = null
                it[sessionCookies] = null
                it[sessionExpiresAt] = null
                it[updatedAt] = currentDateTime
            } > 0
        }

    open suspend fun getSesHospedajesIntegrationsByAccountId(accountId: Uuid): List<SesHospedajesIntegration> =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountSesHospedajesIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .selectAll()
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountIntegrationsTable.vendor eq IntegrationVendor.SesHospedajes) and
                        (AccountsTable.deletedAt.isNull())
                }.map { SesHospedajesIntegration.from(it, encryptionService) }
        }

    /**
     * Arrendador codes of the account's SES Hospedajes integrations. Lean lookup used to
     * validate property establishment codes without decrypting credentials.
     */
    open suspend fun getSesArrendadorCodesByAccountId(accountId: Uuid): List<String> =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountSesHospedajesIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .select(AccountSesHospedajesIntegrationsTable.arrendadorCode)
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountIntegrationsTable.vendor eq IntegrationVendor.SesHospedajes) and
                        (AccountsTable.deletedAt.isNull())
                }.map { it[AccountSesHospedajesIntegrationsTable.arrendadorCode] }
        }

    /**
     * Batch-load the gate-relevant state (enabled + credential-flagged) of the given SES
     * integrations, keyed by integration id. Used to derive `ses_status` for API properties from
     * their assigned integration without decrypting credentials. Missing ids are simply absent.
     */
    open suspend fun getSesIntegrationGateStates(integrationIds: Set<Uuid>): Map<Uuid, SesIntegrationGateState> {
        if (integrationIds.isEmpty()) return emptyMap()
        return dbQuery {
            AccountSesHospedajesIntegrationsTable
                .select(
                    AccountSesHospedajesIntegrationsTable.id,
                    AccountSesHospedajesIntegrationsTable.enabled,
                    AccountSesHospedajesIntegrationsTable.credentialsErrorCode,
                ).where { AccountSesHospedajesIntegrationsTable.id inList integrationIds }
                .associate {
                    it[AccountSesHospedajesIntegrationsTable.id] to
                        SesIntegrationGateState(
                            enabled = it[AccountSesHospedajesIntegrationsTable.enabled],
                            credentialsFlagged = it[AccountSesHospedajesIntegrationsTable.credentialsErrorCode] != null,
                        )
                }
        }
    }

    /**
     * Of the given accounts, returns the ids of those that have an enabled SES Hospedajes
     * integration. Batch lookup used to compute the `ses_enabled` flag on property responses
     * without decrypting credentials.
     */
    open suspend fun getAccountIdsWithEnabledSesIntegration(accountIds: Set<Uuid>): Set<Uuid> {
        if (accountIds.isEmpty()) return emptySet()
        return dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountSesHospedajesIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .select(AccountIntegrationsTable.accountId)
                .where {
                    (AccountIntegrationsTable.accountId inList accountIds) and
                        (AccountIntegrationsTable.vendor eq IntegrationVendor.SesHospedajes) and
                        (AccountSesHospedajesIntegrationsTable.enabled eq true) and
                        (AccountsTable.deletedAt.isNull())
                }.map { it[AccountIntegrationsTable.accountId] }
                .toSet()
        }
    }

    open suspend fun getSesHospedajesIntegrationById(
        accountId: Uuid,
        integrationId: Uuid,
    ): SesHospedajesIntegration? =
        dbQuery {
            AccountIntegrationsTable
                .join(
                    AccountSesHospedajesIntegrationsTable,
                    JoinType.INNER,
                    AccountIntegrationsTable.id,
                    AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                ).join(AccountsTable, JoinType.INNER, AccountIntegrationsTable.accountId, AccountsTable.id)
                .selectAll()
                .where {
                    (AccountIntegrationsTable.accountId eq accountId) and
                        (AccountSesHospedajesIntegrationsTable.id eq integrationId) and
                        (AccountsTable.deletedAt.isNull())
                }.singleOrNull()
                ?.let { SesHospedajesIntegration.from(it, encryptionService) }
        }

    open suspend fun createSesHospedajesIntegration(
        accountId: Uuid,
        arrendadorCode: String,
        sesUsername: String,
        sesPassword: String,
        rhDirectBookingsEnabled: Boolean? = null,
    ): SesHospedajesIntegration =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()
            val rhEnabled = rhDirectBookingsEnabled ?: true

            val accountIntegrationId =
                AccountIntegrationsTable.insert {
                    it[AccountIntegrationsTable.accountId] = accountId
                    it[AccountIntegrationsTable.vendor] = IntegrationVendor.SesHospedajes
                    it[AccountIntegrationsTable.createdAt] = currentDateTime
                    it[AccountIntegrationsTable.updatedAt] = currentDateTime
                }[AccountIntegrationsTable.id]

            val newIntegrationId =
                AccountSesHospedajesIntegrationsTable.insert {
                    it[AccountSesHospedajesIntegrationsTable.accountIntegrationId] = accountIntegrationId
                    it[AccountSesHospedajesIntegrationsTable.arrendadorCode] = arrendadorCode
                    it[AccountSesHospedajesIntegrationsTable.sesUsername] = sesUsername
                    it[AccountSesHospedajesIntegrationsTable.sesPassword] = encryptionService.encrypt(sesPassword)
                    it[AccountSesHospedajesIntegrationsTable.rhDirectBookingsEnabled] = rhEnabled
                    it[AccountSesHospedajesIntegrationsTable.createdAt] = currentDateTime
                    it[AccountSesHospedajesIntegrationsTable.updatedAt] = currentDateTime
                }[AccountSesHospedajesIntegrationsTable.id]

            SesHospedajesIntegration(
                id = newIntegrationId,
                accountIntegrationId = accountIntegrationId,
                accountId = accountId,
                arrendadorCode = arrendadorCode,
                sesUsername = sesUsername,
                sesPassword = sesPassword,
                enabled = true,
                rhDirectBookingsEnabled = rhEnabled,
                createdAt = currentDateTime.toLocalDateTime(),
                updatedAt = currentDateTime.toLocalDateTime(),
            )
        }

    open suspend fun updateSesHospedajesIntegration(
        accountId: Uuid,
        integrationId: Uuid,
        arrendadorCode: String,
        sesUsername: String,
        sesPassword: String,
        rhDirectBookingsEnabled: Boolean? = null,
    ): Boolean {
        return dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            val exists =
                AccountIntegrationsTable
                    .join(
                        AccountSesHospedajesIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountSesHospedajesIntegrationsTable.id eq integrationId)
                    }.singleOrNull() ?: return@dbQuery false

            val accountIntegrationId = exists[AccountSesHospedajesIntegrationsTable.accountIntegrationId]

            AccountSesHospedajesIntegrationsTable.update(
                { AccountSesHospedajesIntegrationsTable.accountIntegrationId eq accountIntegrationId },
            ) {
                it[AccountSesHospedajesIntegrationsTable.arrendadorCode] = arrendadorCode
                it[AccountSesHospedajesIntegrationsTable.sesUsername] = sesUsername
                it[AccountSesHospedajesIntegrationsTable.sesPassword] = encryptionService.encrypt(sesPassword)
                if (rhDirectBookingsEnabled != null) {
                    it[AccountSesHospedajesIntegrationsTable.rhDirectBookingsEnabled] = rhDirectBookingsEnabled
                }
                // Optimistically clear any credential-error flag so the submission/poll jobs resume
                // on the next tick. The handler's SES ping (catalogo) only proves username/password,
                // not the arrendador code / portal config behind códigos 10103/10119/10120 — if the
                // saved values still carry that problem, the next tick's submission re-flags the
                // integration and re-notifies the owners (bounded: one email per save attempt).
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorCode] = null
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorMessage] = null
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorAt] = null
                it[AccountSesHospedajesIntegrationsTable.updatedAt] = currentDateTime
            } > 0
        }
    }

    /**
     * Flag a SES integration as having invalid credentials (SES rejected a call with a
     * credential-family código). Only writes when the flag is not already set — first-writer-wins —
     * and returns whether *this* call made the null→error transition, so the caller can fire the
     * one-time owner notification without duplicates when several bookings fail in the same tick.
     */
    open suspend fun markSesCredentialsError(
        integrationId: Uuid,
        codigo: Int,
        descripcion: String?,
    ): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()
            AccountSesHospedajesIntegrationsTable.update(
                {
                    (AccountSesHospedajesIntegrationsTable.id eq integrationId) and
                        (AccountSesHospedajesIntegrationsTable.credentialsErrorCode.isNull())
                },
            ) {
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorCode] = codigo
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorMessage] = descripcion?.take(1000)
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorAt] = currentDateTime
                it[AccountSesHospedajesIntegrationsTable.updatedAt] = currentDateTime
            } > 0
        }

    /**
     * Clear the credential-error flag of a SES integration (credentials proved valid again — a SES
     * call succeeded, or the owner re-saved verified credentials). Returns whether a flag was cleared.
     */
    open suspend fun clearSesCredentialsError(integrationId: Uuid): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()
            AccountSesHospedajesIntegrationsTable.update(
                {
                    (AccountSesHospedajesIntegrationsTable.id eq integrationId) and
                        (AccountSesHospedajesIntegrationsTable.credentialsErrorCode.isNotNull())
                },
            ) {
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorCode] = null
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorMessage] = null
                it[AccountSesHospedajesIntegrationsTable.credentialsErrorAt] = null
                it[AccountSesHospedajesIntegrationsTable.updatedAt] = currentDateTime
            } > 0
        }

    /**
     * Updates only the lightweight feature settings of a SES Hospedajes integration
     * (no credentials, no SES verification). Returns false when the integration does not
     * exist for the given account. See [updateSesHospedajesIntegration] for credential updates.
     */
    open suspend fun updateSesHospedajesSettings(
        accountId: Uuid,
        integrationId: Uuid,
        rhDirectBookingsEnabled: Boolean,
    ): Boolean =
        dbQuery {
            val now = Clock.System.now()
            val currentDateTime = now.toJavaInstant().atZone(madridZone.toJavaZoneId()).toOffsetDateTime()

            val accountIntegrationId =
                AccountIntegrationsTable
                    .join(
                        AccountSesHospedajesIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountSesHospedajesIntegrationsTable.id eq integrationId)
                    }.singleOrNull()
                    ?.get(AccountSesHospedajesIntegrationsTable.accountIntegrationId) ?: return@dbQuery false

            AccountSesHospedajesIntegrationsTable.update(
                { AccountSesHospedajesIntegrationsTable.accountIntegrationId eq accountIntegrationId },
            ) {
                it[AccountSesHospedajesIntegrationsTable.rhDirectBookingsEnabled] = rhDirectBookingsEnabled
                it[AccountSesHospedajesIntegrationsTable.updatedAt] = currentDateTime
            } > 0
        }

    open suspend fun deleteSesHospedajesIntegration(
        accountId: Uuid,
        integrationId: Uuid,
    ): Boolean =
        dbQuery {
            val accountIntegrationId =
                AccountIntegrationsTable
                    .join(
                        AccountSesHospedajesIntegrationsTable,
                        JoinType.INNER,
                        AccountIntegrationsTable.id,
                        AccountSesHospedajesIntegrationsTable.accountIntegrationId,
                    ).selectAll()
                    .where {
                        (AccountIntegrationsTable.accountId eq accountId) and
                            (AccountSesHospedajesIntegrationsTable.id eq integrationId)
                    }.singleOrNull()
                    ?.get(AccountSesHospedajesIntegrationsTable.accountIntegrationId) ?: return@dbQuery false

            AccountIntegrationsTable.deleteWhere {
                AccountIntegrationsTable.id eq accountIntegrationId
            } > 0
        }

    data class AllIntegrations(
        val nuki: List<NukiIntegration> = emptyList(),
        val unifi: List<UnifiIntegration> = emptyList(),
        val sesHospedajes: List<SesHospedajesIntegration> = emptyList(),
    )

    open suspend fun getAllIntegrationsByAccountId(accountId: Uuid): AllIntegrations {
        val nuki = getNukiIntegrationsByAccountId(accountId)
        val unifi = getUnifiIntegrationsByAccountId(accountId)
        val sesHospedajes = getSesHospedajesIntegrationsByAccountId(accountId)

        return AllIntegrations(
            nuki = nuki,
            unifi = unifi,
            sesHospedajes = sesHospedajes,
        )
    }
}
