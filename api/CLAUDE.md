# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin/Ktor web application for property management (Kasify). 
It uses PostgreSQL with Exposed ORM, Koin for dependency injection, and follows a layered architecture pattern.

## Development Commands

```bash
# Development
./gradlew run                          # Run the server (port 8080)
./gradlew test                         # Run tests
./gradlew build                        # Build everything

# Production
./gradlew buildFatJar                  # Build executable JAR
./gradlew buildImage                   # Build Docker image
./gradlew runDocker                    # Run using Docker
```

## Requirements and description

Document with the project goals at 'Requirements.md'

## Architecture

The application follows a clean layered architecture:

- **Routes** (`routes/`): HTTP endpoint handlers with authentication
- **Services** (`services/`): Busine/mss logic layer with database operations
- **Models** (`model/`): Domain objects and data classes
- **Database** (`db/`): Exposed ORM table definitions and enums
- **Security** (`security/`): Authentication principals and JWT handling

### Notes
- All types returned by the API should be located in model/api, do not return the types from the DB layer.
- Do not include the types in the route classes, always add them into the model/api folder

## Database Setup

### Quick Start with Docker

```bash
# Start PostgreSQL container
docker run -d \
  --name kasify-postgres \
  -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=kasify \
  postgres:15-alpine

# Wait for database to be ready (optional)
docker exec kasify-postgres pg_isready -U postgres

# Execute the schema
docker exec -i kasify-postgres psql -U postgres -d kasify < db.sql

# Stop the container
docker stop kasify-postgres

# Start the container again
docker start kasify-postgres

# Remove the container (when done)
docker rm -f kasify-postgres
```

**Connection**: PostgreSQL at `localhost:5432/kasify`
**Schema**: Located in `db.sql` with sample data

## Authentication

Authentication is handled by Kinde.com. 
We receive the tokens by cookies or via the Bearer token authentication header, tokens are validated and verified
For the initial development we use a hardcoded development token `abc123`. This will get replaced once we get close to production.
All property endpoints require authentication and enforce role-based access control through the `property_access` table.

In addition to Kinde JWTs, the API accepts self-issued personal access tokens (PATs) prefixed `kas_pat_`. `AuthenticationService` branches on the prefix and delegates to `ApiTokenService.validate`, which looks up the SHA-256 hash in `api_tokens`, rejects revoked/expired tokens (`auth.token_revoked`, `auth.token_expired`, `auth.token_invalid`), and builds the same `KasifyUserPrincipal` the JWT path produces. PAT-authenticated requests carry `authMethod = AuthMethod.ApiToken` and are rejected by the `/account/tokens` routes themselves so a leaked PAT cannot mint or revoke tokens.

## SES Hospedajes data retention

Spanish law requires guest data submitted to the SES Hospedajes registry to be retained for **3 years**. A booking is considered submitted once `bookings.ses_submitted_at` is set. Once submitted, owner-side guest mutations are **locked**:

- `PATCH /guests/{id}` (edit) and `DELETE /guests/{id}` (delete) both reject the request with `409 Conflict` and code `guest.ses_locked` when the guest's booking has been submitted (`requireNotSesSubmitted` in `routes/Guests.kt`).
- The dashboard mirrors this by disabling the Edit/Delete options in the guest-details drawer when the booking is SES-synced.

When adding new guest write paths, apply the same `ses_submitted_at` guard so retained records can't be altered or removed.

### SES communication state model

SES communication state lives in the `ses_communication` projection table — one row per `(booking, communication_type, operation)`, where `communication_type ∈ {pv, rh}` and `operation ∈ {alta, anulacion}`. All four combinations share one lifecycle (`submitted → processing → accepted | rejected | failed`). `ses_hospedajes_submissions` remains the append-only wire log, linked via `ses_communication_id`.

- **PV (Parte de Viajeros)** is filed for **all channels**, after check-in. It reports the **registered guests** (the `guests` list); `numPersonas = guests.size`.
- **RH (Reserva de Hospedaje)** is filed **only for direct bookings** (Airbnb/Booking self-file it), and only when the account opts in. It is filed **pre-check-in**, before any guest registers, so it carries the booking's **principal guest** (`booking.guest_first_name`/`guest_last_name`/`guest_email`/`guest_phone`) as the titular — **not** the PV `guests` list — and `numPersonas` comes from the booking's own adult/child/infant counts. SES additionally requires the titular to carry **at least one contact** (teléfono o correo — spec §3.1.1 RH persona); filing without one returns a partial error (`codigoEstado 6`). The gate (`channel == Direct` + `rhDirectBookingsEnabled` + establishment code + a principal guest name + a phone-or-email) lives in `SesHospedajesService.checkRhEligibility`, so both the auto job and the admin `submit-rh` endpoint enforce it identically.
- **Anulaciones** are discovery-driven: `SesHospedajesSubmissionJob` finds accepted altas of cancelled bookings without an active anulación (`SesCommunicationsRepository.acceptedAltasForCancelledBookingsWithoutAnulacion`) and sends each — there is **no cancel hook**. RH must be polled to a terminal estado because an anulación can only reference the código of an `accepted` alta.

**Auto-job time windows** (in the discovery queries `BookingsRepository.getBookingsPendingPvAlta`/`getBookingsPendingRhAlta` only — the admin manual Submit buttons are not gated by these and can still override):

| Type | Auto-submit window |
|---|---|
| PV | While the booking is active, up to **one day after check-out** (`check-out date + 1 day`). Bookings that ended earlier than that are no longer picked up. |
| RH | Up to and including the **day the booking starts** (`check-in date`). Once the start day has passed, RH is no longer picked up. |

The windows gate **first discovery only**: a booking with a `failed` alta (transport error or credential-family rejection) stays eligible after its window closes (`failedAltaExists`), so an outage that outlives the window can't permanently swallow a filing.

**Rejected-alta retry gate** (`BookingsRepository.noUnchangedRejectedAltaExists`, applied to both discovery queries): a `rejected` alta is a SES validation/business rejection (a non-credential `codigo != 0` on submit, or a terminal error estado on poll). Re-filing the identical data would just be rejected again, so the auto job does **not** re-pick a rejected booking until its data changes — the gate compares the alta's `last_attempt_at` against `bookings.updated_at`. That column is the watermark: every booking edit bumps it, and so does every guest add/edit/delete (`GuestsRepository.touchBooking`, since PV reports the guest list). A `failed` alta (transient SOAP/transport error) is **not** gated and keeps retrying every tick. As always, the admin manual Submit buttons bypass discovery entirely and can force a resubmit regardless.

**Property ↔ integration assignment (strict).** An account may hold several SES integrations, but each property files through exactly one: `properties.ses_integration_id` (set together with the establishment code). There is **no fallback** — an unassigned property is ineligible (`ses_hospedajes.not_eligible.property_not_linked`) and never enters discovery. Setting an establishment code on an unassigned property requires `ses_integration_id` (else 400 `ses_hospedajes.integration_required`); configuring a property assigned to a *different* integration returns 409 `ses_hospedajes.property_already_configured` — reassignment is two-step (clear the code on the old integration, which unassigns both columns, then configure anew). The whole pipeline (submit, cancel, poll — `resolveIntegrationForProperty`, and the poll job's per-integration grouping) resolves credentials from the property's assigned integration; `ses_enabled` on the API property also requires the assignment.

**Failure classification** (`SesFailureClassifier`): every SES failure — synchronous submit códigos and async estado-6 per-communication error texts — is classified into a cause with a **scope**, and the blocking state is recorded at that scope: `IntegrationCredentials` (credential-family códigos → integration flag), `PropertyConfig` (unknown establishment code, matched on the SES error text "No existe un establecimiento" → property flag), `BookingData` (everything else → comm row `rejected`). Classification is conservative: an unrecognized error degrades to the booking-scoped `rejected` path, never to a pause. Transport failures bypass the classifier (`failed`, retried every tick).

**Booking-rejected notification** (`SesBookingRejectedNotifier`): a booking-scoped rejection also emails the account owners (`NotificationType.SesBookingRejected`, with a `bookings?bookingId=` deep link) so they know which booking to fix — fired from the sync `BookingData` path and from the poll's estado-6 path (alta only, and never when the estado-6 error is the establishment one, which fires the property-config email instead). Per-event, no pause flag: frequency is bounded by the rejected-alta retry gate (one rejection per data change). Terminal estados 2/3/99 stay email-silent — they are not owner-fixable.

**Credential-error pause** (`SesCredentialsGuard`): a credential-family Layer-1 código (10103/10107/10119/10120 — bad WS credentials / SES portal config) is an *account* problem, not a booking problem. Instead of `rejected`, the comm row is recorded as `failed` (retry-eligible) and the integration is flagged (`account_ses_hospedajes_integrations.credentials_error_code/message/at`). While flagged, PV/RH alta discovery, anulación discovery, and the poll job all **skip that integration's properties** (shared SQL gate in `db/SesCredentialsErrorGate.kt`, in-memory check in `SesHospedajesPollJob`). The null→error transition is first-writer-wins and fires exactly one owner email (`NotificationType.SesCredentialsError`) + Sentry event per outage. The flag clears when the owner re-saves credentials or when any later SES call through the integration succeeds (self-heal, e.g. an admin manual Submit — which is deliberately not paused and returns 409 `ses_hospedajes.invalid_credentials` on a credential error); the next tick then resumes all pending submissions automatically. The save-time clear is optimistic (catalogo only verifies username/password, not the arrendador code behind 10103/10119/10120) — a still-wrong config re-flags and re-notifies on the next tick, bounded to one email per save attempt. The pause is **scoped to the flagged integration's assigned properties** (`sesPropertyLinkedToUsableIntegration` in `db/SesCredentialsErrorGate.kt`): sibling integrations of the same account keep filing for their own properties.

**Property-config pause** (`SesPropertyConfigGuard`): the property-scoped analog of the credential pause, for a wrong establishment code (SES reports it asynchronously as an estado-6 data-validation error — "No existe un establecimiento para ese código"). The comm row is recorded as `failed` (retry-eligible, via `markPolled`'s `statusOverride` on the poll path) — **not** `rejected`, so recovery never depends on the booking-edit watermark — and the property is flagged (`properties.ses_config_error_message/at`). While flagged, PV/RH alta discovery and anulación discovery **skip only that property** (`sesPropertySubmittable` in `db/SesCredentialsErrorGate.kt`); sibling properties on the same integration keep filing. **Polls are deliberately not paused**: the property's in-flight lotes must reach a terminal estado, otherwise a stale lote polled after the code was fixed would re-flag the property with its old error. First-writer-wins transition → one owner email (`NotificationType.SesPropertyConfigError`) + Sentry event per outage. The flag clears when the owner re-saves the property's establishment code (optimistic clear in `PropertiesRepository`, same semantics as the credential re-save) or when a later polled estado for the property succeeds; the next tick then re-picks the `failed` comms automatically. A manual admin Submit is not paused; if it surfaces the error it returns 409 `ses_hospedajes.property_config_error`. The API property exposes the derived `ses_status` (`not_configured | disabled | paused_credentials | paused_config | ok`) plus `ses_config_error_message` for the dashboard banner.

| Variable | Default | Notes |
|---|---|---|
| `SES_RH_AUTO_SUBMIT_ENABLED` | `false` | When `true`, `SesHospedajesSubmissionJob` auto-files RH altas for eligible direct bookings. Governs **only new RH altas** — PV altas and all anulaciones (PV/RH) always run. Runtime flag: flip it without a redeploy. Validate the RH alta → poll → anulación lifecycle against `SesEnvironment.TEST` before enabling in production. |
| `SES_ENDPOINT_URL` | unset | Overrides the SES SOAP endpoint URL in `SesSoapClient` — used to point the API at the local mock server (`./gradlew runSesMock`, see the "SES Hospedajes mock server" section of `api/README.md`) when the official test server is down. Never set in production; a WARN is logged when active. |
| `SES_SUBMISSION_CHECKIN_MARGIN_HOURS` | `3` | Grace period (hours) after the scheduled check-in time before a PV alta enters auto-job discovery (`BookingsRepository.pvAltaPendingCondition`), giving the guest time to actually arrive. Set to `0` in development to submit immediately when testing the pipeline end-to-end. Read once at startup. |

### Local SES mock server

`src/test/kotlin/sesmock/SesMockServer.kt` (test source set — never in the production JAR) mocks the SES SOAP endpoint: catalogo, comunicación alta/anulación (PV + RH, decoding the Base64+ZIP solicitud), and consultaLote with the real async lifecycle (estado 5 → 4 → 1 over `SES_MOCK_PROCESS_SECONDS`, default 10s; `codigoComunicacion` only appears in the poll response, like production).

```bash
./gradlew runSesMock                    # mock on :8888, Basic auth test/test
SES_ENDPOINT_URL=http://localhost:8888/hospedajes-web/ws/v1/comunicacion ./gradlew run
```

Error scenarios are triggered by magic values in the submitted data: booking `referencia` containing `REJECT` (synchronous rejection, comm row `rejected`), `PARTIAL` (estado 6 + per-comm error), `STUCK` (never terminal — exercises the poll loop), `BOOM` (estado 3); establishment code `E-UNKNOWN` reproduces the real "No existe un establecimiento" error (→ the **property-config pause**: comm `failed`, property flagged, cleared by re-saving the code); an establishment code `E<código>` (mapped Layer-1 códigos only: `E10103`/`E10107`/`E10119`/`E10120`/`E10121`/`E10999`) forces that código synchronously — the credential-family ones exercise the credential-error pause per property, `E10121` the plain rejected path; a WS username containing `badcreds` returns código 10107 "Usuario incorrecto" on every operation incl. polls (catalogo still succeeds so the integration can be saved). A guest `nombre`/`apellido` containing `boom`, `partial` or `e-unknown` (case-insensitive) also polls estado 6 — handy for the rejected → fix the guest → resubmit loop, since guest edits reopen the retry gate. Inspect received lotes/códigos at `GET /__admin/state`, reset with `POST /__admin/reset`. Full docs: the "SES Hospedajes mock server" section of `api/README.md`.

## API Structure

- **Base URL**: `http://localhost:8080`
- **Auth Header**: `Authorization: Bearer {TOKEN}`

## API Versioning

All business endpoints are served under a `/v1` prefix (e.g. `GET /v1/properties`, `POST /v1/checkin/{code}/guests`). New route files should wrap their `routing { ... }` block with `versionedRouting { ... }` (see `Versioning.kt`), which centralizes the prefix in one constant (`API_VERSION_PREFIX`).

The following endpoints are **unversioned by design** and stay at the root:

| Endpoint | Reason |
|---|---|
| `/health` | Operational liveness probe |
| `/metrics` | Internal Prometheus scrape target |
| `/openapi.json`, `/openapi.yaml` | The spec describing the current `v1` API |
| `/` | Top-level info endpoint |

### Compatibility within `v1`

> **Pre-launch exception (in force until further notice).** The product has almost no users yet, so a `/v2` bump is not worth its cost. Do **not** flag or block a change for being backwards-incompatible, and do not suggest bumping the API version. Make the breaking change directly in `v1` (tighten validation, rename a field, change a type, etc.) and just keep the frontend and any callers in sync. This overrides the rules below. Revisit once we have real traffic.

Within `v1` only **additive, backwards-compatible** changes are permitted:

- ✅ adding a new endpoint, an optional request field, an extra response field, a new error code, a new query parameter
- ❌ removing or renaming a field/endpoint, narrowing a type, tightening validation, repurposing an error code

Breaking changes ship as `/v2`.

## Error Responses

All non-2xx responses share the same JSON shape, serialized by `StatusPages.kt`:

```json
{ "code": "invitation.expired", "message": "Invitation is expired..." }
```

The HTTP status comes from the exception type; the `code` is the stable, machine-readable identifier the frontend uses to render a localized message. The `message` is a human-readable English fallback for logs, tests, and clients that don't localize.

### Where error codes and messages live

All error codes and messages must be declared as `const val`s in `routes/Errors.kt` — never inline strings in routes, services, or DTOs. This keeps the public API contract auditable in one place and makes renames safe.

Naming convention inside `Errors.kt`:

- `CODE_<DOMAIN>_<CONDITION>` for the stable identifier returned to clients (e.g. `CODE_CHECKIN_BOOKING_FULL = "checkin.booking_full"`).
- `ERROR_<DOMAIN>_<CONDITION>` for the English message (e.g. `ERROR_CHECKIN_BOOKING_FULL = "All guests for this booking are already registered"`).

Group constants by feature with a section comment. When adding a new code, add the constant in `Errors.kt`, reference it from the throw site, and add the matching translation key in both `dashboard/public/locales/{en,es}/common.json` (see "When a new code is added" below).

### Raising errors from routes

Throw one of the typed exceptions in `routes/exceptions/`. Each has a `code` parameter with a sensible default — override it when the client needs to distinguish this error from other errors of the same HTTP status.

| Exception | HTTP status | Default code |
|---|---|---|
| `BadParamRequestException` | 400 | `invalid_parameter` |
| `NotAuthorizedException` | 401 | (see class) |
| `ForbiddenException` | 403 | (see class) |
| `NotFoundException` | 404 | `not_found` |
| `ConflictException` | 409 | `conflict` |
| `NotAcceptableException` | 406 | (see class) |
| `InternalServerException` | 500 | (see class) |

```kotlin
throw BadParamRequestException(
    Errors.ERROR_INVITATION_EXPIRED,
    code = Errors.CODE_INVITATION_EXPIRED,
)
```

### Code naming convention

- Dotted, lowercase, snake_case segments: `<domain>.<condition>` (e.g. `invitation.expired`, `invitation.already_member`, `checkin.booking_full`).
- The first segment groups the error by feature/resource; the second names the condition.
- Codes are a **public API contract** — once a frontend localizes a code, renaming it breaks the client. Prefer adding new codes over renaming existing ones.

### When a new code is added

1. The frontend needs matching translations for both locales (`dashboard/public/locales/{en,es}/common.json`) under `<feature>.error.<code>` — see `dashboard/CLAUDE.md` for the consumer pattern.
2. If the error is user-facing, coordinate with the dashboard before shipping so the code isn't surfaced as a raw English fallback.

## Testing

When adding new features, follow the existing service layer pattern and add corresponding tests.

## Standards

ktlint used to lint code following Kotlin code conventions

```bash
./gradlew ktlintCheck   # Checks code
./gradlew ktlintFormat  # Formats code
```

Important rules:
- Never use wildcard imports

## Configuration

- **Application config**: `src/main/resources/application.yaml`
- **Logging**: `src/main/resources/logback.xml`
- **API docs**: `../docs/apis/openapi.json` (generated from the running server via `./gradlew fetchOpenApiSpec`, served by the Zudoku portal in `/docs`)

### Logging

The console output is colorized text by default (good for `./gradlew run` in IntelliJ). Set the env vars below to switch to structured JSON output for production / log shippers (Loki, CloudWatch, etc.). The rotated file appenders under `logs/` keep their parseable text format regardless.

| Variable | Default | Notes |
|---|---|---|
| `LOGBACK_LEVEL` | `DEBUG` | Root log level, also applied to the app's `eu.kasify` loggers. Set to `INFO` in prod. |
| `LOG_ROOT_APPENDER` | `STDOUT` | Set to `JSON_STDOUT` for structured JSON on stdout. |
| `LOG_REQ_APPENDER` | `STDOUT_REQ` | Same idea for the HTTP request logger. Use `JSON_STDOUT`. |

The JSON encoder also stamps `env=<ENVIRONMENT>` on every line, reusing the existing `ENVIRONMENT` env var (`development` / `production`) that the rest of the app already keys off.

Use `withLoggingContext("integration_id" to id, ...) { }` from `io.github.oshai.kotlinlogging` to attach structured fields. The Quartz job entry points already wrap their `runBlocking` with `MDCContext()` so MDC values survive coroutine suspensions (e.g. `dbQuery`); do the same for any new background coroutine root or MDC values will silently drop after the first thread switch. The JSON encoder in `logback.xml` exposes a fixed allowlist of MDC keys — add new keys to the `<includeMdcKeyName>` list there.

### Email (Resend)

Transactional emails are sent via Resend. The backend has two layers:
- `plugins/resend/` — `ResendClient` interface + `ResendClientImpl` (real) + `LoggingResendClient` (dev/test stub that logs instead of sending).
- `services/email/` — `EmailService` (domain-level send methods, fire-and-forget) and `EmailTemplateRenderer` (`{{placeholder}}` substitution with HTML escaping).

Templates live at `src/main/resources/email/<name>.<locale>.{subject.txt,html,txt}`. Three files per locale: subject, HTML body, plain-text body. HTML values are auto-escaped by the renderer — templates can use `{{inviter_name}}` safely even when the name contains user input.

**Env vars:**
| Variable | Purpose |
|---|---|
| `RESEND_API_KEY` | Resend API key (required when `EMAIL_ENABLED=true`) |
| `EMAIL_ENABLED` | `true` to send real emails; otherwise `LoggingResendClient` logs the rendered payload |
| `EMAIL_FROM_ADDRESS` | Sender, e.g. `Kasify <noreply@kasify.eu>` |
| `DASHBOARD_BASE_URL` | Used to build accept links (e.g. `https://kasify.eu` in prod) |

Email is one delivery channel of the notifications system (below) — `EmailChannel` wraps `ResendClient` + `EmailTemplateRenderer`. Producers no longer call a mailer inline; they enqueue a notification and a background worker delivers it.

## Notifications (transactional outbox + channels)

Notifications use a **transactional outbox**: a producer persists a `notifications` row and returns immediately; the `NotificationDispatchJob` Quartz worker (every minute) delivers it off the request path with retries. This keeps the request fast and makes delivery durable and retryable instead of fire-and-forget.

**Flow:** `NotificationService.enqueue*` (producer, `services/notifications/`) writes one outbox row **per channel** (`ChannelResolver` decides: email always; Telegram only when enabled and the user has linked). The worker `claimBatch`es due rows (`FOR UPDATE SKIP LOCKED`), resolves the recipient (`RecipientResolver`), and delivers via the matching `NotificationChannel` (`EmailChannel` / `TelegramChannel`).

- **Statuses** (`notification_status`): `pending → processing → sent` | retry back to `pending` (exponential backoff in `next_attempt_at`) | `dead` (non-retryable error or attempts exhausted → Sentry). A reaper resets stale `processing` rows.
- **Recipient** is a user (`user_id`) or a raw `email_override` (invitations target an address before the invitee has an account → email-only).
- **Payload** (`payload` JSONB) stores rendering inputs (placeholders + locale + `templateKey`), not rendered text. `event_type` = `NotificationType.dbValue` = the template key under `resources/{email,telegram}/`.
- **Adding an event:** add a `NotificationType`, its templates, and a `NotificationService.enqueue*` helper. Enqueueing is best-effort (logged, never bubbles) — a notification failure must never roll back the business operation.
- **Channels:** the worker (`NotificationDispatchJob.defaultChannels`) and Koin both build channels from config; a channel with no implementation (e.g. Telegram disabled) dead-letters stray rows rather than silently dropping them.
- **Metrics:** `notifications_{sent,failed,dead}_total{channel}` via `NotificationMetrics` (exported when `METRICS_TOKEN` is set).

### Telegram

Telegram is an opt-in complement to email. The user links their account via a deep-link + webhook flow: `POST /v1/account/telegram/link` returns a `t.me/<bot>?start=<token>` link; the user presses Start; Telegram calls `POST /v1/telegram/webhook` (public, authenticated by the secret token it echoes), which maps the chat id to the user. `GET`/`DELETE /v1/account/telegram` read/unlink. `plugins/telegram/` holds the Bot API client (`TelegramClientImpl`, with `LoggingTelegramClient` stub).

**Env vars** (all required when `TELEGRAM_ENABLED=true`, validated in `TelegramConfig.fromEnv()`):
| Variable | Purpose |
|---|---|
| `TELEGRAM_ENABLED` | `true` to enable Telegram; otherwise the logging stub is used and linking is disabled |
| `TELEGRAM_BOT_TOKEN` | Bot API token (from BotFather, see below) |
| `TELEGRAM_BOT_USERNAME` | Bot username (without `@`), for `t.me/<bot>` deep links |
| `TELEGRAM_WEBHOOK_SECRET` | Shared secret checked against the `X-Telegram-Bot-Api-Secret-Token` header |

**Creating the bot (BotFather).** Each environment has its own bot, token, and webhook — a staging deploy must never hijack the prod webhook.

1. In Telegram, message **@BotFather** → `/newbot`.
2. Choose a **display name** and a **username** (must end in `bot`, globally unique). Suggested naming:

   | Env | Display name | Username (`TELEGRAM_BOT_USERNAME`) |
   |---|---|---|
   | Production | `Kasify` | `KasifyBot` (fallbacks: `KasifyAppBot`, `GetKasifyBot`) |
   | Dev / staging | `Kasify · Dev` | `KasifyDevBot` (fallback: `KasifyStagingBot`) |

   Keep the prod display name bare (`Kasify`) for brand trust; put the "I send booking, check-in, and access notifications" blurb in the bot bio via BotFather `/setdescription`. Make dev visibly distinct (the `· Dev` suffix and a separate username/avatar) so nobody links a real chat to the wrong environment.
3. BotFather returns the token (`123456789:AA…`) → that's `TELEGRAM_BOT_TOKEN`. Treat it like a password (env/secrets only, never committed); `/revoke` rotates it if leaked.
4. `TELEGRAM_BOT_USERNAME` is the username from step 2 (no `@`). `TELEGRAM_WEBHOOK_SECRET` is **not** from BotFather — generate your own high-entropy string (e.g. `openssl rand -hex 32`).

**Register the webhook with Telegram (one-time ops step, per environment)** — not done at startup so a deploy never clobbers the prod webhook:
```bash
curl "https://api.telegram.org/bot<TELEGRAM_BOT_TOKEN>/setWebhook?url=<PUBLIC_BASE_URL>/v1/telegram/webhook&secret_token=<TELEGRAM_WEBHOOK_SECRET>"
```

## Development Notes

- Use coroutines for database operations via `dbQuery` wrapper
- All database queries should verify user permissions through property access table
- HTTP request examples available in `src/main/kotlin/requests.http`
- **OpenAPI documentation** is generated at `../docs/apis/openapi.json` by running `./gradlew fetchOpenApiSpec` against a locally running server (port 8080). The spec is assembled at runtime from route `.describe { }` blocks in `src/main/kotlin/routes/`, so update those when the API surface changes (new/removed endpoints, changed request/response shapes, query/path parameters, error codes, auth) and re-run the task. The Zudoku portal in `/docs` reads the committed JSON.
- All Repository classes must return a DTO modeled in /model
- All API endpoints must return a class from /model/api
- `db.sql` is the current schema and the baseline for fresh installs. Every time a table changes, update `db.sql` and also add a numbered SQL script under `migrations/` (create the folder on first use) so existing databases can be upgraded by hand.
- Everytime a tupe in the model/api is updated, update the corresponding type in the dashboard/types
- Do not use wildcard imports

## Rate Limiting

Implementation lives in `src/main/kotlin/RateLimiting.kt`. All limits use a 1-minute rolling window. The `RateLimit` plugin is only installed when `RATE_LIMIT_ENFORCE=true` (the default); otherwise route wraps no-op so traffic is not throttled but the configured limits are still logged at startup.

### Buckets

| Provider (`RateLimitProviders.X`) | Key                                | Default | Where it's applied                                                |
|-----------------------------------|------------------------------------|---------|-------------------------------------------------------------------|
| `Principal` — PAT branch          | `pat:<tokenId>`                    | 100/min | Every authenticated route, when `authMethod == ApiToken`          |
| `Principal` — JWT branch          | `user:<userId>`                    | 100/min | Every authenticated route, when `authMethod == Jwt`               |
| `IpCheckin`                       | `call.request.origin.remoteHost`   | 30/min  | `/checkin/...`                                                    |
| `CheckinCode`                     | `parameters["code"]`               | 10/min  | `/checkin/{code}/...` (route must define `{code}` in its path)    |
| `IpGeo`                           | `call.request.origin.remoteHost`   | 50/min  | `/geo/...`                                                        |
| `IpOpenApi`                       | `call.request.origin.remoteHost`   | 10/min  | `/openapi.json`, `/openapi.yaml`                                  |

The single `Principal` provider serves both PAT and JWT callers — `rateLimiter` resolves the limit per call from `KasifyUserPrincipal.authMethod`, and `requestKey` namespaces the bucket so PATs and users never collide.

### Applying limits in routes

Do **not** call Ktor's `rateLimit(name) { ... }` directly. Use the wrappers:

- `authenticatedAndRateLimited { ... }` — replaces `authenticate(AUTH_BEARER, AUTH_COOKIE) { ... }`; adds the `Principal` bucket inside the auth block (so the principal exists when the bucket key is computed). Use for every authenticated route.
- `maybeRateLimit(RateLimitProviders.X) { ... }` — for the IP/code-keyed providers on unauthenticated routes. Stack wraps when multiple buckets apply (`/checkin/{code}/...` wraps both `IpCheckin` and `CheckinCode`).

Both helpers check that the `RateLimit` plugin is installed before wrapping, which keeps test fixtures that omit the plugin from breaking at startup.

### Configuration (env vars)

| Variable                               | Default | Notes                                                              |
|----------------------------------------|---------|--------------------------------------------------------------------|
| `RATE_LIMIT_ENFORCE`                   | `true`  | `false` → plugin not installed, wraps become no-ops                |
| `RATE_LIMIT_PAT_PER_MIN`               | `100`   |                                                                    |
| `RATE_LIMIT_USER_PER_MIN`              | `100`   |                                                                    |
| `RATE_LIMIT_IP_CHECKIN_PER_MIN`        | `30`    |                                                                    |
| `RATE_LIMIT_CHECKIN_CODE_PER_MIN`      | `10`    |                                                                    |
| `RATE_LIMIT_IP_GEO_PER_MIN`            | `50`    |                                                                    |
| `RATE_LIMIT_IP_OPENAPI_PER_MIN`        | `10`    |                                                                    |

### When changing a default

The numeric default appears in three files. Update all three together — drift here shows up as wrong limits in the public docs:

1. `src/main/kotlin/RateLimiting.kt` — the source of truth (`RateLimitsConfig.fromEnv()`).
2. `src/main/kotlin/OpenApi.kt` — the `info.description` summary in the generated OpenAPI spec.
3. `docs/pages/developers/getting-started.mdx` — the public bucket table rendered by the Zudoku portal.

### 429 response

```json
{ "code": "rate_limit.exceeded", "message": "Too many requests. Retry after 42 seconds." }
```

The HTTP status is 429 with a `Retry-After` header in seconds; the body uses the standard error envelope (handled by `StatusPages.kt`).