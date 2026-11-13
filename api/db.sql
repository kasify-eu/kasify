-- ============================================================================
-- CREATE TABLES & TYPES
-- ============================================================================
-- 5432:5432
CREATE TABLE accounts
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE users
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    external_user_id TEXT NOT NULL,
    name            TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    email           TEXT UNIQUE NOT NULL,
    locale     VARCHAR(5) NOT NULL DEFAULT 'es', -- Preferred language for notifications (email/Telegram)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE properties
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    address     TEXT        NOT NULL,
    city        TEXT        NOT NULL,
    zipcode     TEXT        NOT NULL,
    tzone      TEXT        NOT NULL,

    -- Who created this property
    created_by_id                UUID NOT NULL REFERENCES users(id),

    -- Cleaning task automation settings
    default_cleaner_id           UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    cleaning_time_offset_minutes INT NOT NULL DEFAULT 60,  -- Minutes after checkout to schedule cleaning
    cleaning_duration_minutes    INT NOT NULL DEFAULT 120, -- Default cleaning duration

    -- Stay times (used to populate booking calendar events)
    checkin_time                 TIME NOT NULL DEFAULT '17:00:00',
    checkout_time                TIME NOT NULL DEFAULT '11:00:00',

    -- SES Hospedajes fields
    ses_establishment_code VARCHAR(10),   -- codigoEstablecimiento assigned by SES
    -- The SES integration that files for this property (one integration per property; strict —
    -- an unassigned property is ineligible for SES). FK added after the integrations table below.
    ses_integration_id     UUID,
    ses_establishment_type VARCHAR(10),   -- tipo de establecimiento (SES catalog code)
    -- SES property-config error state (wrong establishment code). While set,
    -- alta/anulación discovery skips this property (polls continue); cleared when the code is
    -- re-saved or a later SES estado for the property succeeds. Set/cleared together.
    ses_config_error_message TEXT NULL,
    ses_config_error_at      TIMESTAMPTZ NULL,
    has_internet           BOOLEAN DEFAULT true, -- internet availability flag for SES
    rooms_count            INTEGER NOT NULL DEFAULT 1, -- number of rooms (numHabitaciones for SES)
    -- NIF/CIF of the actual arrendador. Property-level (not integration-level) because the
    -- Kasify account may be a manager operating properties owned by different arrendadores.
    arrendador_nif         TEXT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);


-- Property Service Enum (type of work the crew member performs)
CREATE TYPE property_service AS ENUM (
    'cleaning',
    'maintenance',
    'pool',
    'gardening'
);

-- Property Access Level Enum
CREATE TYPE property_access_level AS ENUM (
    'manager',
    'staff',
    'contractor'
);

-- Property Access for crew members (external users granted access to specific properties)
-- Account members (user.account_id = property.account_id) have implicit owner access
CREATE TABLE property_access
(
    id           UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id      UUID                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    property_id  UUID                   NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    service      property_service       NULL DEFAULT NULL,
    access_level property_access_level  NOT NULL DEFAULT 'contractor',
    created_at   TIMESTAMPTZ            NOT NULL DEFAULT now(),
    UNIQUE (user_id, property_id)
);

CREATE INDEX idx_property_access_user ON property_access (user_id);
CREATE INDEX idx_property_access_property ON property_access (property_id);

-- Polymorphic discriminator for calendar_events: identifies which related
-- table (if any) a calendar event row joins to.
--   'booking' → join bookings on calendar_event_id
--   'task'    → join tasks on calendar_event_id (read tasks.task_type for the kind of work)
--   'block'   → no join, owner-blocked dates
CREATE TYPE event_type AS ENUM (
    'booking',
    'task',
    'block'
);

CREATE TABLE calendar_events
(
    id             UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties (id) ON DELETE CASCADE,

    from_datetime TIMESTAMPTZ NOT NULL,
    to_datetime   TIMESTAMPTZ NOT NULL,

    type           event_type  NOT NULL,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_calendar_events_property_dates
    ON calendar_events (property_id, from_datetime, to_datetime);

-- Index on event type for filtering by discriminator (booking, task, block)
CREATE INDEX idx_calendar_events_type
    ON calendar_events (type);

CREATE TYPE booking_status AS ENUM (
    'confirmed',
    'cancelled'
);

CREATE TYPE booking_channel AS ENUM (
    'airbnb',
    'booking',
    'direct',
    'vrbo',
    'google'
);

-- How a booking was paid for. Generic, channel-agnostic values — deliberately not the SES
-- Hospedajes tipoPago codes; the mapping to SES codes lives in the SES layer (PaymentType.toSesTipoPago).
CREATE TYPE payment_type AS ENUM (
    'platform',
    'cash',
    'credit_card',
    'bank_transfer',
    'mobile',
    'pay_at_property',
    'gift_card',
    'other'
);

-- Enum mirroring the codigoEstado returned by SES `consultaLote`
-- (see documentation/ses-hospedajes/ses-hospedajes-developer-info.md:14-20).
-- Each value is prefixed with the SES numeric code; `99_codigo_desconocido`
-- is a Kasify-internal sentinel for unrecognised codes coming back from SES.
CREATE TYPE ses_codigo_estado AS ENUM (
    '1_tramitado_sin_errores',
    '2_errores_cabecera_formato',
    '3_error_inesperado',
    '4_en_proceso',
    '5_pendiente',
    '6_tramitado_con_errores_parciales',
    '99_codigo_desconocido'
);

CREATE TABLE bookings
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    -- Each booking must be linked to exactly 1 calendar event
    calendar_event_id UUID UNIQUE     NOT NULL REFERENCES calendar_events (id) ON DELETE CASCADE,

    -- Denormalized property reference (for fast filtering)
    property_id       UUID            NOT NULL REFERENCES properties (id) ON DELETE CASCADE,

    channel           booking_channel NOT NULL,
    channel_id        TEXT            NOT NULL,
    -- Public check-in code: high-entropy lookup key for /v1/checkin/{code}.
    -- DB-generated so every insert path (Kotlin, raw SQL, fixtures) gets one.
    checkin_code      TEXT            NOT NULL UNIQUE DEFAULT upper(replace(gen_random_uuid()::text, '-', '')),
    status            booking_status  NOT NULL DEFAULT 'confirmed',
    payment_type      payment_type    NOT NULL DEFAULT 'platform',
    notes            TEXT NULL,

    -- Kasify checkin process
    checkin_completed_at                TIMESTAMPTZ NULL,

    -- SES Hospedajes state now lives in the ses_communication projection table
    -- (one row per booking × type × operation), not in per-booking columns.

    -- Informacion del huesped principal
    guest_first_name TEXT NULL,
    guest_last_name  TEXT NULL,
    guest_email      TEXT NULL,
    guest_phone      TEXT NULL,
    adults_count     INT NULL,
    children_count   INT NULL,
    infants_count    INT NULL,

    -- Informacion operativa
    special_requests TEXT NULL,
    arrival_time     TIME NULL,
    departure_time   TIME NULL,

    -- Owner's private per-stay guest review (owner-only at the API layer).
    -- would_host_again is tri-state: NULL = not reviewed, TRUE/FALSE = explicit decision.
    would_host_again   BOOLEAN NULL,
    guest_review_notes TEXT NULL,

    -- Timestamps
    last_sync_at     TIMESTAMPTZ NULL,
    cancelled_at     TIMESTAMPTZ NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index on channel_id for lookups by external booking reference
CREATE INDEX idx_bookings_channel_id ON bookings (channel_id);

-- Index on property_id for queries filtering bookings by property
-- Used heavily in: getBookingsByProperty, getCurrentBooking, count
CREATE INDEX idx_bookings_property_id ON bookings (property_id);

-- Index on status for filtering active/pending/cancelled bookings
-- Used heavily in: count (filters by confirmed status)
CREATE INDEX idx_bookings_status ON bookings (status);

-- Composite index for common query pattern: property + status
-- Optimizes queries like "get all confirmed bookings for a property"
CREATE INDEX idx_bookings_property_status ON bookings (property_id, status);


-- ============================================================================
-- GEOGRAPHIC LOOKUP TABLES
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE countries (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name     TEXT    NOT NULL,
    name_es  TEXT    NOT NULL,
    alpha_2  CHAR(2) NOT NULL UNIQUE,
    alpha_3  CHAR(3) NOT NULL UNIQUE
);

CREATE INDEX idx_countries_alpha_3 ON countries (alpha_3);
CREATE INDEX idx_countries_name ON countries USING gin (name gin_trgm_ops);
CREATE INDEX idx_countries_name_es ON countries USING gin (name_es gin_trgm_ops);

CREATE TABLE provinces (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id      UUID        NOT NULL REFERENCES countries(id),
    name            TEXT        NOT NULL,
    code_iso_3166_2 VARCHAR(6)  NOT NULL,
    ine_code_pro    VARCHAR(2)  NOT NULL,
    UNIQUE(country_id, ine_code_pro)
);

CREATE INDEX idx_provinces_country ON provinces (country_id);
CREATE INDEX idx_provinces_name ON provinces USING gin (name gin_trgm_ops);

CREATE TABLE cities (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id   UUID        NOT NULL REFERENCES countries(id),
    province_id  UUID        NOT NULL REFERENCES provinces(id),
    name         TEXT        NOT NULL,
    ine_code     VARCHAR(5)  NOT NULL UNIQUE,
    ine_code_mun VARCHAR(3)  NOT NULL
);

CREATE INDEX idx_cities_province ON cities (province_id);
CREATE INDEX idx_cities_name ON cities USING gin (name gin_trgm_ops);

-- Structured address columns on properties (FKs added after geo tables exist)
ALTER TABLE properties
    ADD COLUMN country_id  UUID NOT NULL REFERENCES countries(id),
    ADD COLUMN province_id UUID REFERENCES provinces(id),
    ADD COLUMN city_id     UUID REFERENCES cities(id);

-- ============================================================================
-- GUESTS
-- ============================================================================

CREATE TYPE gender AS ENUM (
    'male',
    'female',
    'other'
);

CREATE TYPE document_type AS ENUM (
    'dni',
    'nie',
    'passport',
    'other'
);

CREATE TABLE guests (
    id              UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    property_id     UUID NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    first_name      TEXT NOT NULL,
    last_name       TEXT NOT NULL,
    second_last_name    TEXT NULL,
    nationality_country_id UUID NULL REFERENCES countries(id),
    date_of_birth   DATE NULL,
    relationship_code VARCHAR(5),
    sex               gender  NULL,
    residence_country_id UUID NULL REFERENCES countries(id),
    residence_province_id UUID NULL REFERENCES provinces(id),
    residence_city_id UUID NULL REFERENCES cities(id),
    residence_city    TEXT NULL,
    residence_address TEXT NULL,
    residence_postal_code TEXT NULL,
    document_type   document_type NULL,
    document_number TEXT NULL,
    document_support_number TEXT NULL,
    email           TEXT NULL,
    phone           TEXT NULL,
    phone_prefix    TEXT NULL,
    -- Marketing opt-in (GDPR Art. 6(1)(a) + LSSI Art. 21): when the guest consented
    -- and the privacy-policy version in force (e.g. 'privacy-policy-v1.0', matching
    -- the "Version:" line in dashboard/app/legal/privacy-policy.md), plus the token
    -- authenticating their unsubscribe link.
    -- Null unless the guest (18+, with email) ticked the optional checkbox.
    marketing_consent_at TIMESTAMPTZ NULL,
    marketing_consent_version TEXT NULL,
    marketing_unsubscribe_token UUID NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_guests_bookings ON guests (booking_id);
CREATE INDEX idx_guests_properties ON guests (property_id);
CREATE UNIQUE INDEX idx_guests_marketing_unsubscribe_token
    ON guests (marketing_unsubscribe_token)
    WHERE marketing_unsubscribe_token IS NOT NULL;

-- Unsubscribed addresses (lowercased). suppressed_at is the latest unsubscribe
-- for the address (refreshed on every unsubscribe): it withdraws every guest
-- consent recorded at or before it (marketing_consent_at <= suppressed_at), in
-- every account. A consent recorded later counts for the guest row that
-- recorded it only (fresh consent supersedes the old opt-out for that row). A
-- consent never deletes the row: the address on a guest registration is
-- self-asserted, so it must not lift anyone else's opt-out. guest_id and
-- created_at (first unsubscribe) are audit-only.
CREATE TABLE marketing_suppressions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    guest_id      UUID NULL REFERENCES guests (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    suppressed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE property_channel_integrations
(
    id                    UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    property_id           UUID            NOT NULL REFERENCES properties (id) ON DELETE CASCADE,

    -- Integration details
    channel               booking_channel NOT NULL,
    url                   TEXT            NOT NULL, -- The iCal URL

    -- Sync configuration
    sync_enabled          BOOLEAN         NOT NULL DEFAULT true,
    sync_interval_minutes INT             NOT NULL DEFAULT 60,
    last_sync_at          TIMESTAMPTZ     NULL,
    next_sync_at          TIMESTAMPTZ     NULL,

    -- Status and error handling
    last_error            TEXT NULL,
    error_count           INT             NOT NULL DEFAULT 0,

    -- Metadata
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),

    UNIQUE (property_id, url) -- Prevent duplicate URLs per property
);

CREATE INDEX idx_property_integrations_property ON property_channel_integrations (property_id);

-- Account Integrations Management
-- Accounts can have multiple integrations from different vendors (Nuki, Unifi, SES Hospedajes, etc.)
-- Each integration has a generic record in account_integrations and vendor-specific record in account_<vendor>_integrations

CREATE TYPE integration_vendor AS ENUM ('nuki', 'unifi', 'seshospedajes');

CREATE TABLE account_integrations
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    account_id      UUID                 NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    vendor          integration_vendor   NOT NULL,
    created_at      TIMESTAMPTZ          NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ          NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_integrations_account ON account_integrations (account_id);

CREATE TABLE account_nuki_integrations
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Each nuki integration must be linked to exactly 1 account integration
    account_integration_id   UUID UNIQUE     NOT NULL REFERENCES account_integrations (id) ON DELETE CASCADE,
    nuki_token               TEXT            NOT NULL, -- TODO: Encrypt at application layer

    -- Timestamps
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_nuki_integrations_integration ON account_nuki_integrations (account_integration_id);

CREATE TABLE account_unifi_integrations
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Each unifi integration must be linked to exactly 1 account integration
    account_integration_id   UUID UNIQUE     NOT NULL REFERENCES account_integrations (id) ON DELETE CASCADE,
    unifi_username           TEXT            NOT NULL,
    unifi_password           TEXT            NOT NULL, -- TODO: Encrypt at application layer
    unifi_console_url        TEXT            NOT NULL,

    -- Session caching columns
    session_csrf_token TEXT,                           -- Encrypted CSRF token from Unifi login
    session_cookies    TEXT,                           -- Encrypted serialized cookies from Unifi login
    session_expires_at TIMESTAMPTZ,                    -- When the cached session expires

    -- Timestamps
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_unifi_integrations_integration ON account_unifi_integrations (account_integration_id);

CREATE TABLE account_ses_hospedajes_integrations
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Each SES Hospedajes integration must be linked to exactly 1 account integration
    account_integration_id   UUID UNIQUE     NOT NULL REFERENCES account_integrations (id) ON DELETE CASCADE,
    arrendador_code          VARCHAR(10)     NOT NULL, -- codigoArrendador assigned by SES
    ses_username             TEXT            NOT NULL,
    ses_password             TEXT            NOT NULL, -- Encrypted at application layer
    enabled                  BOOLEAN         NOT NULL DEFAULT true,
    -- Whether to send the Reserva de Hospedaje (RH) communication for Direct bookings.
    -- RH is only ever sent for Direct bookings; OTA channels report reservations to SES themselves.
    rh_direct_bookings_enabled BOOLEAN       NOT NULL DEFAULT true,

    -- Credential-error state (SES códigos 10103/10107/10119/10120): set when SES rejects a
    -- submission because of the account's portal configuration/credentials. While set, the
    -- submission and poll jobs skip all work for this integration. Cleared when the owner
    -- re-saves credentials or a later SES call succeeds. NULL code = credentials believed valid.
    -- The three columns are always set and cleared together.
    credentials_error_code    INTEGER        NULL,
    credentials_error_message TEXT           NULL,
    credentials_error_at      TIMESTAMPTZ    NULL,

    -- Timestamps
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_ses_hospedajes_integrations_integration ON account_ses_hospedajes_integrations (account_integration_id);

-- properties.ses_integration_id FK (deferred: properties is created before this table).
-- ON DELETE SET NULL: deleting an integration unassigns its properties.
ALTER TABLE properties ADD CONSTRAINT fk_properties_ses_integration
    FOREIGN KEY (ses_integration_id) REFERENCES account_ses_hospedajes_integrations (id) ON DELETE SET NULL;
CREATE INDEX idx_properties_ses_integration ON properties (ses_integration_id)
    WHERE ses_integration_id IS NOT NULL;

CREATE TABLE ses_catalog_cache
(
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_type             VARCHAR(50)     NOT NULL,   -- TIPO_DOCUMENTO, SEXO, TIPO_PAGO, TIPO_PARENTESCO, TIPO_ESTABLECIMIENTO
    code                     VARCHAR(20)     NOT NULL,
    description              TEXT            NOT NULL,
    fetched_at               TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE(catalog_type, code)
);

-- Append-only audit log of every SES Hospedajes interaction.
-- One row per SOAP call: either an initial submission ('submit') or a
-- follow-up batch status query ('query'). Polls insert new rows rather
-- than updating, so the full timeline survives outages and re-submissions.
CREATE TABLE ses_hospedajes_submissions
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    booking_id            UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    operation             TEXT        NOT NULL CHECK (operation IN ('submit', 'query')),
    attempted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    respuesta_codigo      INT NULL,                  -- SOAP <codigo>: 0 = accepted; NULL if SES did not respond
    respuesta_descripcion TEXT NULL,                 -- SOAP <descripcion>
    ses_lote              TEXT NULL,                 -- submit: lote returned by SES; query: lote we queried
    ses_estado            ses_codigo_estado NULL,    -- query only: result of consultaLote; NULL for submit rows
    ses_estado_desc       TEXT NULL,                 -- query only: <descEstado>
    error_text            TEXT NULL,                 -- exception message when SES is unreachable
    response_xml          TEXT NULL,                 -- full SOAP envelope received; NULL if no response
    request_xml           TEXT NULL                  -- inner solicitud XML sent; NULL for query rows
);

CREATE INDEX idx_ses_submissions_booking
    ON ses_hospedajes_submissions (booking_id, attempted_at DESC);

-- SES communication state projection: one row per (booking, communication_type, operation).
-- An *alta* files a communication; an *anulación* annuls a previously-filed one. Both share the same
-- lifecycle (submit -> lote -> consultaLote poll -> terminal estado). This table is the current-state
-- projection; ses_hospedajes_submissions remains the append-only wire log, now linked via
-- ses_communication_id.

CREATE TYPE ses_communication_type AS ENUM ('pv', 'rh');
CREATE TYPE ses_communication_operation AS ENUM ('alta', 'anulacion');
CREATE TYPE ses_communication_status AS ENUM (
    'submitted',   -- SES accepted the submit (codigo=0), lote returned, awaiting poll
    'processing',  -- poll returned a non-terminal estado (4_en_proceso / 5_pendiente)
    'accepted',    -- poll returned terminal success (1_tramitado_sin_errores)
    'rejected',    -- SES rejected synchronously (codigo!=0) or a terminal error estado
    'failed'       -- transport/SOAP error; will retry
);

CREATE TABLE ses_communication (
    id                   UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    booking_id           UUID NOT NULL              REFERENCES bookings (id) ON DELETE CASCADE,
    communication_type   ses_communication_type      NOT NULL,
    operation            ses_communication_operation NOT NULL,
    cancels_id           UUID NULL                  REFERENCES ses_communication (id),  -- anulacion -> the alta it annuls
    status               ses_communication_status    NOT NULL,
    lote                 TEXT NULL,                  -- batch id SES returned for THIS filing
    codigo_comunicacion  TEXT NULL,                  -- per-communication code; needed to issue an anulacion
    estado               ses_codigo_estado NULL,     -- consultaLote result; NULL until polled
    estado_desc          TEXT NULL,
    submitted_at         TIMESTAMPTZ NULL,
    last_attempt_at      TIMESTAMPTZ NULL,
    last_error           TEXT NULL,
    created_at           TIMESTAMPTZ NOT NULL        DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL        DEFAULT now(),
    UNIQUE (booking_id, communication_type, operation)
);

-- Supports the anulación discovery query: accepted altas are candidates for annulment
-- once their booking is cancelled (the join to bookings.status happens in the query).
CREATE INDEX idx_ses_communication_accepted_alta
    ON ses_communication (booking_id)
    WHERE operation = 'alta' AND status = 'accepted';

CREATE INDEX idx_ses_communication_polling
    ON ses_communication (lote)
    WHERE lote IS NOT NULL
      AND (estado IS NULL OR estado IN ('4_en_proceso', '5_pendiente'));

CREATE INDEX idx_ses_communication_booking
    ON ses_communication (booking_id);

ALTER TABLE ses_hospedajes_submissions
    ADD COLUMN ses_communication_id UUID NULL REFERENCES ses_communication (id);


-- ============================================================================
-- GUEST REGISTRY (libro registro de viajeros)
-- ============================================================================
-- Registro documental / libro registro de viajeros (RD 933/2021, Orden INT/1922/2003).
-- Every alta accepted by SES leaves an immutable, self-contained entry here. Deliberately
-- NO foreign keys: entries must survive future hard-deletes/purges of bookings, properties
-- and accounts for the 3-year legal retention window, and a GDPR account erasure must be
-- able to remove everything personal EXCEPT these rows until they expire.

CREATE TABLE guest_registry_entries
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Plain UUIDs (no REFERENCES) — see header comment.
    account_id             UUID   NOT NULL,
    property_id            UUID   NOT NULL,
    booking_id             UUID   NOT NULL,
    -- Correlative per establishment, as the libro registro requires.
    entry_number           BIGINT NOT NULL,
    communication_type     ses_communication_type NOT NULL,

    -- Denormalized so the entry stays readable if account/property are ever purged.
    ses_arrendador_code    TEXT NOT NULL,
    arrendador_nif         TEXT NULL,     -- copied from properties; NULL if not configured yet
    ses_establishment_code TEXT NOT NULL,
    property_name          TEXT NOT NULL,
    property_address       TEXT NOT NULL,
    postal_code            TEXT NULL,
    city                   TEXT NULL,
    province               TEXT NULL,

    -- Transaction data (RD 933/2021 anexo I.3) as exportable columns.
    contract_reference     TEXT NOT NULL,
    contract_date          DATE NOT NULL,
    booking_channel        booking_channel NOT NULL,
    checkin_date           DATE NOT NULL,
    checkout_date          DATE NOT NULL, -- 3-year purge anchor
    num_guests             INT  NOT NULL,

    -- Frozen content of the parte as communicated.
    snapshot               JSONB NOT NULL, -- contract + payment + guests (+ signature ref later)
    request_xml            TEXT  NOT NULL, -- exact solicitud XML sent to SES

    ses_lote               TEXT NULL,
    codigo_comunicacion    TEXT NULL,
    ses_estado             ses_codigo_estado NULL, -- final consultaLote outcome, set by the poll job
    submitted_at           TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (property_id, entry_number),
    -- ses_lote identifies one SES submission (a fresh lote per submit), so a persistence
    -- retry of the same submission is idempotent while a re-filed alta (rejected on poll,
    -- corrected, force-resubmitted) appends a new entry instead of being dropped.
    UNIQUE (booking_id, communication_type, ses_lote)
);

-- Export: entries for a property filtered by stay dates, ordered by entry_number.
CREATE INDEX idx_guest_registry_property_checkin
    ON guest_registry_entries (property_id, checkin_date);
-- Purge: retention window scan.
CREATE INDEX idx_guest_registry_checkout
    ON guest_registry_entries (checkout_date);


-- Smart Lock Management
-- Properties can have multiple smart locks from different vendors (Nuki, Unifi, etc.)
-- Each smartlock has a generic record in property_smartlocks and vendor-specific record in property_<vendor>_smartlocks

CREATE TYPE smartlock_vendor AS ENUM ('nuki', 'unifi', 'tedee', 'yale');

CREATE TABLE property_smartlocks
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    property_id     UUID        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,

    vendor          smartlock_vendor NOT NULL,
    name            TEXT        NOT NULL,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_property_smartlocks_property ON property_smartlocks (property_id);

CREATE TABLE property_nuki_smartlocks
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Each nuki smartlock must be linked to exactly 1 smartlock
    property_smartlock_id  UUID UNIQUE     NOT NULL REFERENCES property_smartlocks (id) ON DELETE CASCADE,
    -- The Nuki integration (API key) used to manage this lock
    nuki_integration_id    UUID            NOT NULL REFERENCES account_nuki_integrations (id),
    nuki_smartlock_id      TEXT            NOT NULL,
    -- Timestamps
    created_at             TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_property_nuki_smartlocks_smartlock_id ON property_nuki_smartlocks (property_smartlock_id);
CREATE INDEX idx_property_nuki_smartlocks_integration_id ON property_nuki_smartlocks (nuki_integration_id);

CREATE TABLE property_unifi_smartlocks
(
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Each unifi smartlock must be linked to exactly 1 smartlock
    property_smartlock_id  UUID UNIQUE     NOT NULL REFERENCES property_smartlocks (id) ON DELETE CASCADE,
    door_id  TEXT NOT NULL,
    floor_id TEXT NOT NULL,
    created_at             TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_property_unifi_smartlocks_smartlock_id ON property_unifi_smartlocks (property_smartlock_id);

-- Booking Access Codes Management
-- Each booking can have multiple access codes, one per smartlock
-- Tracks vendor-specific code IDs for deletion and lifecycle management

CREATE TABLE booking_access_codes
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    booking_id            UUID        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    smartlock_id          UUID        NOT NULL REFERENCES property_smartlocks (id) ON DELETE CASCADE,

    -- The access code itself
    access_code           TEXT        NOT NULL,

    -- Vendor-specific code ID (for deletion/updates)
    -- e.g., Nuki auth ID, Unifi schedule ID
    vendor_code_id        TEXT        NULL,

    -- Code lifecycle
    code_start_datetime   TIMESTAMPTZ NOT NULL,
    code_end_datetime     TIMESTAMPTZ NOT NULL,

    -- Timestamps
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ NULL,

    -- Unique constraint: one code per smartlock per booking
    UNIQUE (booking_id, smartlock_id)
);

CREATE INDEX idx_booking_access_codes_booking ON booking_access_codes (booking_id);
CREATE INDEX idx_booking_access_codes_smartlock ON booking_access_codes (smartlock_id);
CREATE INDEX idx_booking_access_codes_vendor_code ON booking_access_codes (vendor_code_id);

-- ============================================================================
-- CREW & TASK MANAGEMENT
-- ============================================================================

-- Crew Invitations
-- Used to invite external users as crew members to specific properties
CREATE TABLE crew_invitations
(
    id          UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    property_id UUID               NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    email       TEXT NULL,                             -- For email invites (optional)
    token       VARCHAR(64) UNIQUE NOT NULL,           -- Invite token/link
    max_uses    INT NULL DEFAULT 1,                    -- NULL = unlimited uses
    uses        INT                NOT NULL DEFAULT 0, -- Current usage count
    expires_at  TIMESTAMPTZ NULL,                      -- NULL = never expires
    created_at  TIMESTAMPTZ        NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ NULL,                      -- Set when first accepted
    access_level property_access_level NOT NULL DEFAULT 'contractor',
    locale      VARCHAR(5)         NOT NULL DEFAULT 'en' -- Locale for the invitation email body
);

CREATE INDEX idx_crew_invitations_token ON crew_invitations (token);
CREATE INDEX idx_crew_invitations_property ON crew_invitations (property_id);

-- Task Status Enum
CREATE TYPE task_status AS ENUM (
    'pending',      -- Created but not yet started
    'in_progress',  -- Crew member has started working
    'completed'     -- Task completed
);

-- Tasks
-- Each task references a calendar_event for unified calendar display
-- Tasks can be auto-generated from bookings or manually created
CREATE TABLE tasks
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    calendar_event_id UUID UNIQUE NOT NULL REFERENCES calendar_events (id) ON DELETE CASCADE,
    property_id       UUID        NOT NULL REFERENCES properties (id) ON DELETE CASCADE,
    booking_id        UUID NULL REFERENCES bookings (id) ON DELETE SET NULL, -- Links to triggering booking (for auto-generated cleaning tasks)
    task_type         property_service NOT NULL,                             -- Kind of work; same vocabulary as crew assignments
    status            task_status NOT NULL DEFAULT 'pending',
    assigned_to       UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    notes             TEXT NULL,
    job_report        TEXT NULL,
    public_token      VARCHAR(64) UNIQUE NULL,                               -- For shareable public links
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ NULL,
    completed_at      TIMESTAMPTZ NULL,
    cancelled_at      TIMESTAMPTZ NULL                                       -- Soft delete (set when linked booking cancelled)
);

CREATE INDEX idx_tasks_calendar_event ON tasks (calendar_event_id);
CREATE INDEX idx_tasks_property ON tasks (property_id);
CREATE INDEX idx_tasks_booking ON tasks (booking_id);
CREATE INDEX idx_tasks_assigned ON tasks (assigned_to);
CREATE INDEX idx_tasks_status ON tasks (status);
-- Covering index for dashboard queries: active tasks per property
CREATE INDEX idx_tasks_property_status ON tasks (property_id, status) INCLUDE (assigned_to);

-- ============================================================================
-- FILES
-- ============================================================================

CREATE TYPE file_type AS ENUM ('cover_image');

CREATE TABLE files
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    account_id        UUID        NOT NULL REFERENCES accounts (id),
    property_id       UUID NULL REFERENCES properties (id) ON DELETE CASCADE,
    file_type         file_type   NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(255) NOT NULL,
    size_bytes        BIGINT      NOT NULL,
    storage_key       TEXT        NOT NULL UNIQUE,
    url               TEXT        NOT NULL,
    uploaded_by       UUID        NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX idx_files_one_cover_per_property ON files (property_id) WHERE file_type = 'cover_image' AND deleted_at IS NULL;
CREATE INDEX idx_files_property_type ON files (property_id, file_type);
CREATE INDEX idx_files_account ON files (account_id);

-- Add cover image URL to properties (denormalized from files table)
ALTER TABLE properties ADD COLUMN cover_image_url TEXT NULL;

-- API tokens (Personal Access Tokens). Self-issued opaque tokens with prefix
-- `kas_pat_`; SHA-256 hash stored in DB.
CREATE TABLE IF NOT EXISTS api_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id      UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    token_hash      TEXT NOT NULL UNIQUE,
    token_prefix    TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NULL,
    last_used_at    TIMESTAMPTZ NULL,
    revoked_at      TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_api_tokens_user_active
    ON api_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_api_tokens_hash_lookup
    ON api_tokens (token_hash) WHERE revoked_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_api_tokens_user_name_active
    ON api_tokens (user_id, name) WHERE revoked_at IS NULL;

-- ============================================================================
-- NOTIFICATIONS (transactional outbox, multi-channel)
-- ============================================================================
-- Transactional outbox for multi-channel notifications. Producers (request handlers, jobs) persist
-- one row per (logical notification x channel) and return immediately; the NotificationDispatchJob
-- worker claims due rows, delivers them via the matching channel, and records the outcome with
-- exponential backoff (next_attempt_at) until a terminal state (sent / dead).
--
-- A recipient is either a registered user (user_id) or a raw email address (email_override) — the
-- latter covers invitation emails sent before the invitee has an account. Rows from the same
-- enqueue share group_id so a multi-channel notification can be traced as one unit.
CREATE TYPE notification_channel AS ENUM ('email', 'telegram');
CREATE TYPE notification_status AS ENUM ('pending', 'processing', 'sent', 'failed', 'dead');

CREATE TABLE notifications (
    id              UUID PRIMARY KEY            DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL,
    user_id         UUID NULL                  REFERENCES users (id) ON DELETE CASCADE,
    email_override  TEXT NULL,
    event_type      TEXT NOT NULL,
    channel         notification_channel NOT NULL,
    payload         JSONB NOT NULL,
    status          notification_status NOT NULL DEFAULT 'pending',
    attempt_count   INT  NOT NULL              DEFAULT 0,
    max_attempts    INT  NOT NULL              DEFAULT 5,
    next_attempt_at TIMESTAMPTZ NOT NULL       DEFAULT now(),
    claimed_at      TIMESTAMPTZ NULL,
    last_error      TEXT NULL,
    created_at      TIMESTAMPTZ NOT NULL       DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL       DEFAULT now()
);

CREATE INDEX idx_notifications_claim
    ON notifications (next_attempt_at)
    WHERE status = 'pending';
CREATE INDEX idx_notifications_group ON notifications (group_id);
CREATE INDEX idx_notifications_user ON notifications (user_id);

-- Per-user Telegram link. Telegram is an opt-in complement to email: a user starts linking from the
-- dashboard (status 'pending', a one-time link_token + expiry), opens the t.me deep link, and the bot
-- webhook reports the chat_id which the backend stores (status 'linked'). Unlinking sets 'revoked' and
-- clears chat_id. The bot token itself is a server secret (env), so chat_id is a low-sensitivity
-- routing id and is stored in plaintext to keep the lookup index usable.
CREATE TYPE telegram_link_status AS ENUM ('pending', 'linked', 'revoked');

CREATE TABLE user_telegram_links (
    id                    UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL          REFERENCES users (id) ON DELETE CASCADE,
    status                telegram_link_status NOT NULL DEFAULT 'pending',
    link_token            TEXT NULL,
    link_token_expires_at TIMESTAMPTZ NULL,
    chat_id               BIGINT NULL,
    telegram_username     TEXT NULL,
    linked_at             TIMESTAMPTZ NULL,
    created_at            TIMESTAMPTZ NOT NULL    DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL    DEFAULT now(),
    UNIQUE (user_id)
);

CREATE UNIQUE INDEX idx_user_telegram_links_token
    ON user_telegram_links (link_token) WHERE link_token IS NOT NULL;
CREATE INDEX idx_user_telegram_links_chat
    ON user_telegram_links (chat_id) WHERE chat_id IS NOT NULL;