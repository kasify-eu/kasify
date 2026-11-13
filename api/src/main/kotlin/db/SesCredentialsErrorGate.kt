package eu.kasify.db

import eu.kasify.db.model.AccountSesHospedajesIntegrationsTable
import eu.kasify.db.model.PropertiesTable
import org.jetbrains.exposed.v1.core.Exists
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Single source of truth for the SES participation gate in SQL (strict assignment model): the
 * property is **explicitly assigned** to a SES integration (`properties.ses_integration_id`) and
 * that integration is enabled and not flagged with a credential error (códigos
 * 10103/10107/10119/10120 — see `SesCredentialsGuard`).
 *
 * Consequences:
 * - An unassigned property never enters discovery — regardless of how many integrations the
 *   account has (no fallback), so there is no per-tick ineligibility noise for it.
 * - A flagged or disabled integration pauses **only its own properties**; the account's other
 *   integrations keep filing.
 *
 * Two gates, by error scope (see `SesFailureClassifier`):
 * - [sesPropertyLinkedToUsableIntegration] — the integration-level gate. Applied to **polls**
 *   (`SesCommunicationsRepository.listPendingPoll`, plus the in-memory re-check in
 *   `SesHospedajesPollJob`): consultaLote uses the integration's WS credentials, so a credential
 *   flag pauses polls too.
 * - [sesPropertySubmittable] — the gate for **new submissions** (PV/RH alta discovery in
 *   `BookingsRepository`, anulación discovery in `SesCommunicationsRepository`): additionally
 *   requires the property itself not to be flagged with a config error (wrong establishment code —
 *   see `SesPropertyConfigGuard`). A property flag deliberately does NOT pause polls: the
 *   property's in-flight lotes must reach a terminal estado, otherwise a stale lote polled after
 *   the code was fixed would re-flag the property with its old error.
 *
 * The credential-error flag clears when the owner re-saves credentials or a later SES call
 * succeeds; the property-config flag clears when the owner re-saves the establishment code or a
 * later SES estado for the property succeeds. Discovery resumes on the next tick either way.
 */
internal fun sesPropertyLinkedToUsableIntegration(): Op<Boolean> =
    PropertiesTable.sesIntegrationId.isNotNull() and
        Exists(
            AccountSesHospedajesIntegrationsTable
                .selectAll()
                .where {
                    (AccountSesHospedajesIntegrationsTable.id eq PropertiesTable.sesIntegrationId) and
                        (AccountSesHospedajesIntegrationsTable.enabled eq true) and
                        (AccountSesHospedajesIntegrationsTable.credentialsErrorCode.isNull())
                },
        )

internal fun sesPropertySubmittable(): Op<Boolean> =
    sesPropertyLinkedToUsableIntegration() and
        PropertiesTable.sesConfigErrorMessage.isNull()
