# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kasify is a property management platform that consolidates bookings from multiple channels (Airbnb, Booking.com, VRBO, Google, Direct) and provides smart lock integration, guest management, and Spanish law compliance for guest registration.

**Repository Structure:**
- `api/` - Kotlin/Ktor backend (port 8080)
- `dashboard/` - React Router v7 frontend (port 5173 dev, 3000 prod)
- `docs/` - Zudoku user documentation and API reference site

## Quick Start

### Backend
```bash
cd api
./gradlew run                    # Start server at localhost:8080
./gradlew test                   # Run tests
./gradlew ktlintFormat           # Format code
```

### Frontend
```bash
cd dashboard
npm install
npm run dev                      # Start at localhost:5173
npm run typecheck                # TypeScript validation
```

### Database (PostgreSQL)
```bash
docker run -d --name kasify-postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=kasify postgres:17.10-alpine
docker exec -i kasify-postgres psql -U postgres -d kasify < api/db.sql
docker exec -i kasify-postgres psql -U postgres -d kasify < api/db_geo_seed.sql
```

## Architecture Overview

### Backend (Kotlin/Ktor)
- **Routes** (`routes/`): HTTP endpoints with authentication
- **Services** (`services/`): Business logic including authorization checks
- **DB** (`db/`): Exposed ORM repositories - all queries verify user permissions via `property_access` table
- **Plugins** (`plugins/`): Third-party integrations (Nuki, Unifi, Airbnb iCal)
- **Security** (`security/`): JWT validation with Kinde OAuth tokens

### Frontend (React Router v7)
- **Routes** (`app/routes/`): File-based routing with SSR
- **Layouts** (`app/layouts/`): AppShell with navigation
- **API Client** (`app/lib/api-client/`): Typed HTTP client with automatic 401 handling
- **Types** (`app/types/`): TypeScript definitions for all domain objects

### Authentication Flow
- Kinde OAuth handles authentication for both frontend and backend
- Backend accepts tokens via Bearer header or cookies
- Frontend automatically handles token expiration via `/session-expired` route (silent refresh)
- Signup flow uses separate `/refresh` route to fetch updated claims

### Multi-Tenancy Model
- Users → Accounts (1:1) - created automatically on registration
- Accounts → Properties (1:many)
- Properties → Bookings, Smart Locks, Calendar Events

### Crew Access Control

Properties can have crew members granted access via the `property_access` table. Access is controlled by two independent concepts:

**Access levels** (`property_access_level` PG enum, `PropertyAccessLevel` Kotlin enum):

| Level | Kotlin | Visibility | Booking access | Notes |
|---|---|---|---|---|
| `manager` | `PropertyAccessLevel.Manager` | MANAGER | Full, including PII | Trusted, can do some write operations |
| `staff` | `PropertyAccessLevel.Staff` | STAFF | All bookings, no PII | Regular employee |
| `contractor` | `PropertyAccessLevel.Contractor` | CONTRACTOR | Blocked (returns 401) | External, task-only access |

**Property services** (`property_service` PG enum, `PropertyService` Kotlin enum): the type of work a crew member performs. Only relevant for `staff` and `contractor`; `manager` has a null service.

| Value | Description |
|---|---|
| `cleaning` | Cleaning tasks |
| `maintenance` | Maintenance/repair tasks |
| `pool` | Pool maintenance |
| `gardening` | Garden/landscaping |

**Key implementation details:**
- `manager` maps to `Visibility.MANAGER` in `SecuritySerializer.toVisibility()` — they see `@VisibleTo(Visibility.MANAGER)` fields but not `OWNER`-only fields
- `staff` maps to `Visibility.STAFF` and `contractor` maps to `Visibility.CONTRACTOR` — manager/owner fields are stripped for both
- Contractors blocked from all booking routes; staff can list bookings without PII fields
- `GET /tasks/{id}` returns `TaskDetailWithContextResponse` (includes top-level `booking_context`) when a contractor accesses a cleaning task; all other roles get `TaskDetailResponse`
- Crew access is granted via invitation tokens; see `POST /properties/{id}/invitations` and `POST /invitations/{token}/accept`

## Key Integrations

| Integration | Purpose |
|-------------|---------|
| Nuki | Smart lock access codes |
| Unifi | Network-based smart locks |
| SES Hospedajes | Spanish guest registry (law compliance) |
| Airbnb/Booking/VRBO | iCal calendar sync |

**SES local development:** the official SES test server is unreliable, so the repo ships a local mock (`cd api && ./gradlew runSesMock`, port 8888). Point the API at it with `SES_ENDPOINT_URL=http://localhost:8888/hospedajes-web/ws/v1/comunicacion` and use SES credentials `test`/`test`. See the "SES Hospedajes mock server" section of `api/README.md`.

## Observability

- **Error monitoring:** Sentry (enabled in production only, requires `SENTRY_DSN` env var)
- **Metrics & dashboards:** the API exposes a `/metrics` endpoint in Prometheus format (protected by `METRICS_TOKEN` env var)

## Code Standards

### Kotlin (Backend)
- ktlint enforces style - run `./gradlew ktlintFormat`
- No wildcard imports
- Use `dbQuery` wrapper for coroutine-based database operations
- All database queries must verify user permissions

### TypeScript (Frontend)
- Strict mode enabled
- Use `~/*` import alias for `app/` directory
- Add new API methods to `ApiClient` class
- All data structures must be typed in `app/types/`

## Testing

```bash
# Backend - run all tests
./gradlew test

# Backend - run specific test
./gradlew test --tests "plugins.nuki.NukiPluginTest"

# Frontend - type checking
npm run typecheck
```

## Project-Specific Documentation

- `api/CLAUDE.md` - Detailed backend development guide
- `api/Requirements.md` - Feature requirements and goals
- `dashboard/CLAUDE.md` - Detailed frontend development guide
- `dashboard/AUTH_FLOW.md` - Authentication and token refresh details