# kasify

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need
  to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [OpenAPI](https://start.ktor.io/p/openapi)                             | Serves OpenAPI documentation                                                       |
| [Swagger](https://start.ktor.io/p/swagger)                             | Serves Swagger UI for your project                                                 |
| [Authentication](https://start.ktor.io/p/auth)                         | Provides extension point for handling the Authorization header                     |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [Exposed](https://start.ktor.io/p/exposed)                             | Adds Exposed database to your application                                          |
| [Koin](https://start.ktor.io/p/koin)                                   | Provides dependency injection                                                      |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                          | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker image to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker image                                     |
| `runSesMock`                  | Run the local SES Hospedajes mock server on port 8888                |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Manual API Testing

The `requests.http` file contains HTTP requests for testing the API manually using IntelliJ's HTTP Client.

### Setup

1. **Create the private environment file** with your Kinde credentials:

   Create `http-client.private.env.json` (this file is gitignored):
   ```json
   {
     "dev": {
       "client_id": "YOUR_KINDE_CLIENT_ID",
       "client_secret": "YOUR_KINDE_CLIENT_SECRET",
       "refresh_token": "YOUR_KINDE_REFRESH_TOKEN"
     }
   }
   ```

2. **Get your credentials from Kinde**:
    - **client_id**: Go to Kinde dashboard > Settings > Applications > Your App > Client ID
    - **client_secret**: Go to Kinde dashboard > Settings > Applications > Your App > Client secret
    - **refresh_token**: Log in to your app, then find the refresh token in your browser's localStorage or cookies

### Running Requests

1. **Select the "dev" environment** from the dropdown at the top-right of the HTTP Client panel
2. **Run the "getToken" request first** (the first request in the file) - this fetches a fresh access token using your
   refresh token
3. **Run any other request** - they will use the token for the rest of your session

The token is stored in memory and remains valid until you close IntelliJ or the token expires.

## SES Hospedajes mock server

The official SES test server (`hospedajes.pre-ses.mir.es`) is often unavailable. The repo includes a local mock (`src/test/kotlin/sesmock/SesMockServer.kt`, test source set — never ships in the production JAR) that speaks the same SOAP protocol, so the unmodified submission pipeline (PV/RH alta → poll `consultaLote` → anulación) runs against it end-to-end.

```bash
# Terminal 1 — start the mock (port 8888)
./gradlew runSesMock

# Terminal 2 — start the API pointed at it
SES_ENDPOINT_URL=http://localhost:8888/hospedajes-web/ws/v1/comunicacion ./gradlew run
```

Use SES credentials `test`/`test` in the integration settings (defaults; override with `SES_MOCK_USER`/`SES_MOCK_PASS`).

Key behaviours:

- Lotes advance Pendiente(5) → En proceso(4) → Tramitado(1) over `SES_MOCK_PROCESS_SECONDS` (default 10; `SES_MOCK_INSTANT=1` skips the wait). `codigoComunicacion` is only returned by the poll, like production.
- Error scenarios are triggered by magic values in the submitted data, see [Error scenarios](#error-scenarios) below.
- Inspect everything received at `GET http://localhost:8888/__admin/state`; wipe state with `POST /__admin/reset`.

### Error scenarios

Set the trigger in the dashboard (booking reference, property establishment code, guest name or integration username) and let the submission job pick the booking up. Guest-name triggers are case-insensitive (`boom`, `Partial`…); booking-reference triggers are **case-sensitive** and must be uppercase (`REJECT`, `PARTIAL`, `STUCK`, `BOOM`).

| Trigger | Where to set it | Mock response | Expected Kasify behaviour |
|---|---|---|---|
| Booking reference contains `REJECT` | Booking `referencia` | Synchronous código `102` on submit, nothing queued | Comm row `rejected`; owners get the *booking rejected* email. Not retried until the booking or its guests change |
| Booking reference contains `PARTIAL` | Booking `referencia` | Lote ends in estado `6` with a per-communication data error | Comm row `rejected` + owner email |
| Guest `nombre`/`apellido` contains `boom`, `partial` or `e-unknown` | Guest name (editable in the dashboard) | Estado `6` with a data error naming the guest | Same as `PARTIAL`, but fixing the guest reopens the retry gate: use it to test *rejected → fix the guest → resubmit* |
| Booking reference contains `BOOM` | Booking `referencia` | Lote ends in estado `3` (unexpected error) | Terminal, no owner email |
| Booking reference contains `STUCK` | Booking `referencia` | Lote stays in estado `4` forever | The poll job keeps polling every tick; useful to exercise the poll loop |
| Establishment code `E-UNKNOWN` | Property establishment code | Estado `6` with "No existe un establecimiento…" | **Property-config pause**: comm `failed`, property flagged (`ses_status = paused_config`), owner email. Cleared by re-saving the code |
| Establishment code `E10103`, `E10107`, `E10119` or `E10120` | Property establishment code | Synchronous código with the real SES description (HTTP 200, nothing queued) | **Credential pause**: comm `failed`, the property's integration is flagged (`paused_credentials`), one owner email. Cleared by re-saving credentials or by any later successful SES call |
| Establishment code `E10121` or `E10999` | Property establishment code | Synchronous código (`Error de validación` / `Error no controlado`) | Not credential-family: handled as a normal booking-scoped rejection |
| WS username contains `badcreds` | Integration credentials | Código `10107` "Usuario incorrecto" on **every** operation, polls included (catalogo still succeeds so the integration can be saved) | Credential pause, same as `E10107` but for the whole integration |

Notes:

- Only the codes listed above fire from an `E<código>` establishment code. Any other value, such as `E12345`, is treated as a normal establishment code.
- `REJECT`, `PARTIAL`, `BOOM`, `E-UNKNOWN` and the guest-name triggers apply to **altas**. `STUCK` applies to any lote.
- Rejected altas are not re-picked automatically until the booking data changes. The manual *Submit* buttons in the admin area ignore pauses and the retry gate, so you can force a resubmit at any time.
- The *Expected Kasify behaviour* column reflects the pipeline described in [`CLAUDE.md`](CLAUDE.md#ses-hospedajes-data-retention); if the two ever disagree, check `SesFailureClassifier` and the mock source.
- To start clean between scenarios: `curl -X POST http://localhost:8888/__admin/reset`.

The mock is configured through environment variables: `SES_MOCK_PORT` (default `8888`), `SES_MOCK_USER` / `SES_MOCK_PASS` (default `test`/`test`), `SES_MOCK_PROCESS_SECONDS` (default `10`) and `SES_MOCK_INSTANT=1`. The source is `src/test/kotlin/sesmock/SesMockServer.kt`.

## Rate Limiting

The API protects every route with one or more rate-limit buckets. Each bucket tracks a rolling 1-minute window; a request is rejected if any applicable bucket is exhausted. Implementation lives in `src/main/kotlin/RateLimiting.kt`.

### Buckets

| Bucket (provider)                  | Keyed by                           | Default | Applies to                                                       |
|------------------------------------|------------------------------------|---------|------------------------------------------------------------------|
| `Principal` (PAT)                  | Token id (`pat:<id>`)              | 100/min | Any authenticated route, requests using a `kas_pat_…` token      |
| `Principal` (JWT user)             | User id (`user:<id>`)              | 100/min | Any authenticated route, requests using a Kinde JWT              |
| `IpCheckin`                        | Client IP                          | 30/min  | `/checkin/...` (unauthenticated guest check-in surface)          |
| `CheckinCode`                      | `{code}` path parameter            | 10/min  | `/checkin/{code}/...` — kills booking-code brute force           |
| `IpGeo`                            | Client IP                          | 50/min  | `/geo/...` lookups                                               |
| `IpOpenApi`                        | Client IP                          | 10/min  | `/openapi.json`, `/openapi.yaml`                                 |

The two `Principal` rows share a single bucket name but compute the limit per-call from the `AuthMethod` on the principal — PATs and JWT users get independent buckets, even with different sizes, because the request key is prefixed (`pat:` vs `user:`).

### 429 response

When a bucket is exhausted the API replies with HTTP `429 Too Many Requests`, a `Retry-After` header (seconds), and the standard error envelope:

```json
{ "code": "rate_limit.exceeded", "message": "Too many requests. Retry after 42 seconds." }
```

### Configuration (env vars)

All limits and the global enforcement switch are configurable via environment variables; defaults match the table above.

| Variable                               | Default | Purpose                                                                 |
|----------------------------------------|---------|-------------------------------------------------------------------------|
| `RATE_LIMIT_ENFORCE`                   | `true`  | Set to `false` for shadow rollout — limits are logged but never applied |
| `RATE_LIMIT_PAT_PER_MIN`               | `100`   | Personal access token bucket size                                       |
| `RATE_LIMIT_USER_PER_MIN`              | `100`   | Authenticated JWT user bucket size                                      |
| `RATE_LIMIT_IP_CHECKIN_PER_MIN`        | `30`    | Per-IP limit for the check-in surface                                   |
| `RATE_LIMIT_CHECKIN_CODE_PER_MIN`      | `10`    | Per booking-code limit for `/checkin/{code}/...`                        |
| `RATE_LIMIT_IP_GEO_PER_MIN`            | `50`    | Per-IP limit for `/geo/...`                                             |
| `RATE_LIMIT_IP_OPENAPI_PER_MIN`        | `10`    | Per-IP limit for the OpenAPI spec                                       |

When `RATE_LIMIT_ENFORCE=false` the `RateLimit` plugin is not installed; the registry logs the configured limits at startup but no traffic is throttled. This lets you ship the wiring and observe baselines before enforcing.

### Applying limits in routes

Route files do not call Ktor's `rateLimit(...)` directly. Two helpers wrap that detail (and silently no-op when the plugin isn't installed, e.g. in tests):

- `authenticatedAndRateLimited { ... }` — drop-in replacement for `authenticate(AUTH_BEARER, AUTH_COOKIE) { ... }` that adds the `Principal` bucket. Use for any authenticated route.
- `maybeRateLimit(RateLimitProviders.X) { ... }` — wraps a route with the named bucket only when enforcement is on. Use for the unauthenticated surfaces (`IpCheckin`, `CheckinCode`, `IpGeo`, `IpOpenApi`).

A route can stack wraps when several buckets apply (e.g. `/checkin/{code}/...` is wrapped in both `IpCheckin` and `CheckinCode`).

### Public documentation

When a default is changed, update three places so the developer-facing docs stay in sync:

1. `src/main/kotlin/RateLimiting.kt` — the source of truth (`RateLimitsConfig.fromEnv()`).
2. `src/main/kotlin/OpenApi.kt` — the `info.description` mentions the headline numbers in the generated spec.
3. `docs/pages/developers/getting-started.mdx` — the public bucket table rendered by the Zudoku portal.
