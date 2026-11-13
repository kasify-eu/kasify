# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the dashboard frontend for Kasify, a property management system. It's built using React Router v7 with TypeScript, Mantine UI components, and Kinde for authentication. The application uses server-side rendering (SSR) by default and communicates with a Kotlin/Ktor backend API.

## Development Commands

```bash
# Development
npm run dev          # Start development server (http://localhost:5173)
npm run build        # Build for production
npm run start        # Start production server
npm run typecheck    # Run TypeScript type checking

# Docker
docker build -t dashboard .
docker run -p 3000:3000 dashboard
```

## Architecture

**Frontend Stack:**
- React Router v7 with file-based routing and layouts
- Mantine v8 for UI components and theming
- TypeScript with strict configuration
- TailwindCSS for additional styling
- Vite for build tooling

**Key Architecture Patterns:**
- **Route-based code splitting**: Routes are organized in `app/routes/` with nested routing
- **Layout system**: `app/layouts/public.tsx` provides AppShell structure with header/navbar
- **Authentication middleware**: `app/middleware/auth.ts` handles Kinde session management
- **API client abstraction**: `app/lib/api-client/` provides typed HTTP client with auth integration
- **Context-based state**: User context managed via React Router's unstable_createContext
- **Type safety**: All API responses and domain models are strongly typed in `app/types/`

## Authentication Flow

Uses Kinde OAuth with the following endpoints:
- `/kinde-auth/login` - Initiate login flow
- `/kinde-auth/logout` - Handle logout
- `/kinde-auth/callback` - Handle OAuth callback

Authentication state is managed in `authMiddleware` and stored in `userContext`. Protected routes automatically redirect unauthenticated users to `/`, which shows the sign-in page.

## API Integration

The backend API runs at `http://0.0.0.0:8080` (configured in `createApiConfig()`). The API client (`app/lib/api-client/index.tsx`) provides:
- Automatic bearer token authentication from Kinde session
- Type-safe request/response handling
- Error handling with custom `ApiClientError` class
- Support for JSON, file uploads, and various HTTP methods

**Available API Methods:**
- `getProperties()` - Fetch all properties
- `getProperty(id)` - Fetch single property
- `getBookings()` - Fetch all bookings
- `getCheckins(date)` - Fetch check-ins from a specific date
- `getCheckouts(date)` - Fetch check-outs from a specific date
- `getBooking(id)` - Fetch single booking
- `getCalendarEvents()` - Fetch calendar events
- `getTimeline()` - Fetch timeline data (legacy)
- `getActivities()` - Fetch activity timeline with grouped activities
- `createProperty(data)` - Create new property
- `deleteProperty(id)` - Delete property
- `getApiTokens()` - List the user's active personal access tokens
- `createApiToken(data)` - Create a personal access token (raw value returned only once)
- `revokeApiToken(id)` - Revoke (soft-delete) a personal access token

### Handling API errors (localized messages via `code`)

Every non-2xx response from the backend carries a stable `code` plus an English `message`. See `api/CLAUDE.md` for the backend contract. On the client, `ApiClient` surfaces failures as `ApiClientError` with three useful fields:

- `status` — HTTP status
- `code` — stable identifier like `invitation.expired`, `checkin.booking_full`
- `message` — English fallback from the backend

**Never display `err.message` directly to users.** It's English-only and written for developers. Translate the `code` instead, with a generic fallback when no translation is registered:

```ts
import { ApiClientError } from "~/lib/api-client";

try {
    await apiClient.acceptInvitation(token);
} catch (err) {
    const errorCode = err instanceof ApiClientError ? err.code : null;
    const key = errorCode ? `propertyJoinModal.error.${errorCode}` : null;
    const translated = key && t(key) !== key ? t(key) : t("propertyJoinModal.joinError");
    setError(translated);
}
```

Conventions:

- Translation key is `<feature>.error.<code>` — e.g. `propertyJoinModal.error.invitation.expired`. Use the same `<feature>` namespace as the rest of the component's strings.
- The `t(key) !== key` check is how we detect a missing translation (i18next returns the key when no value is found) and gate the fallback.
- The generic fallback (`<feature>.submitError`, `<feature>.joinError`, etc.) handles both unknown codes and non-API errors.

**Every new API code requires matching entries in both `public/locales/en/common.json` and `public/locales/es/common.json`** under the `<feature>.error.<code>` key. Without them users see the generic fallback, which is usually too vague to be actionable.

For server actions (React Router actions that catch errors in loaders/actions), pass the `code` through in the action's return value and do the translation in the component:

```ts
// in action
catch (error) {
    const apiError = handleApiError(error);
    return { error: apiError.message, code: apiError.code ?? null, ... };
}

// in component
const errorMessage = actionData?.code && t(`feature.error.${actionData.code}`) !== `feature.error.${actionData.code}`
    ? t(`feature.error.${actionData.code}`)
    : actionData?.error;
```

Existing references: `app/components/property-join-modal/property-join-modal.tsx`, `app/routes/checkin/guest.tsx`, `app/routes/join.tsx`.

## Type System

Domain types are defined in `app/types/` with TypeScript:
- `activities.tsx` - Activity timeline types (Activity, ActivityType, TimelineGroup)
- `bookings.tsx` - Booking and booking response types
- `calendar.tsx` - Calendar event types
- `properties.tsx` - Property and smart lock types
- `integrations.tsx` - Third-party integration types (Nuki, Unifi, SES Hospedajes)
- `timeline.tsx` - Timeline response types (legacy)
- `user.tsx` - User types

## Component Structure

Reusable UI components in `app/components/`:
- `activity-timeline/` - Timeline view displaying activities grouped by date
- `menu/` - MainMenu navigation component with Tabler icons
- `header/` - Header component with logout functionality
- `timeline/` - Legacy timeline component for day view
- `bookings-list.tsx` - Reusable booking list component
- `editable-text-label/` - Inline editable text field
- `full-name-editable-text-label/` - Editable full name component
- `guest-count-editable-label/` - Editable guest count component
- `text-label/` - Static text label component

## Routing Configuration

Routes are configured in `app/routes.ts` using React Router's route configuration. All authenticated routes are nested under the `layouts/public.tsx` layout which provides the AppShell with navigation.

**Main Routes:**
- `/` - Sign-in page with a single button to the SSO (redirects to `/timeline` if authenticated)
- `/timeline` - Activity timeline dashboard (main authenticated view)
- `/properties` - Property management list
- `/properties/new` - Create new property
- `/properties/:id` - Property details and management
- `/bookings` - Booking list and management
- `/bookings/:id` - Booking details
- `/account` - User account settings
- `/kinde-auth/:action` - Authentication flow handlers
- `/signup` - Registration page
- `/health` - Health check endpoint

## Page-Specific Patterns

**Timeline Dashboard** (`app/routes/dashboard/home.tsx`):
- Fetches activities from `/activities` endpoint
- Displays grouped activity timeline with check-ins, check-outs, and current guests
- Uses `ActivityTimeline` component for visualization

**Property Management**:
- Property list view with CRUD operations
- Property detail pages with smart lock management
- Integration with Nuki for smart lock access

**Booking Management**:
- Booking list with filtering by status
- Booking detail pages with guest information
- Integration with SES Hospedajes for Spanish guest registration
- Editable booking details with inline editing components

## Environment Setup

Node.js 24 is required (production image runs on `node:24-alpine`; `.nvmrc` pins the local version). Key configurations:
- TypeScript paths configured with `~/*` alias pointing to `app/*`
- Mantine forced to light theme mode
- PostCSS with Mantine preset for component styling
- React Router v7 with SSR enabled and middleware support

## Localization (i18n)

The dashboard uses `i18next` + `react-i18next` + `remix-i18next` for server-side and client-side translations.

**Supported languages:** English (`en`, default/fallback) and Spanish (`es`).

**Translation files:** `public/locales/{en,es}/common.json` — flat JSON with dot-notation keys (e.g., `"nav.timeline"`, `"properties.title"`). Template variables use `{{varName}}` syntax.

**Configuration:**
- `app/i18n.ts` — shared config (supported languages, fallback, default namespace)
- `app/i18next.server.ts` — SSR setup with `i18next-fs-backend`; locale detected from cookie `kasify_locale` → `Accept-Language` header → fallback `en`
- `app/entry.server.tsx` / `app/entry.client.tsx` — initializes i18next for SSR and client hydration
- `app/root.tsx` — exports `handle = { i18n: "common" }` and sets HTML `lang` attribute

**Usage in components:**
```typescript
import { useTranslation } from "react-i18next";

const { t, i18n } = useTranslation();
t("properties.title")                   // simple key
t("timeline.inDays", { count: 5 })      // with template variable
i18n.changeLanguage("es")               // programmatic language switch
```

**Language selector:** `app/components/language-selector/index.tsx` — rendered at the bottom of the sidebar. On selection it calls `i18n.changeLanguage()` immediately (client) and POSTs to `/api/set-locale` to persist the `kasify_locale` cookie (1-year expiry).

**Adding or updating translations:**
1. Add the key to both `public/locales/en/common.json` and `public/locales/es/common.json`.
2. Use the key in components via `t("your.new.key")`.
3. To add a new language, add its code to `supportedLngs` in `app/i18n.ts`, create `public/locales/{code}/common.json`, and add the option to `LanguageSelector` and the `/api/set-locale` validator.

## Development Guidelines

When working with this codebase:
1. Always use the `~/*` alias for imports from the `app/` directory
2. Create new types in `app/types/` and export them for reuse
3. Add new API methods to the `ApiClient` class in `app/lib/api-client/`
4. Follow the existing component patterns with separate index files
5. Use Mantine components for UI consistency
6. Leverage TypeScript's strict typing for all data structures
7. Test type safety with `npm run typecheck` before committing
8. Use toast notifications instead of `Alert` components for action feedback (success, error). Use `showSuccessToast`, `showErrorToast`, etc. from `~/lib/toast`, triggered via `useEffect` watching `actionData`.