-- Wipes all transactional / user data while preserving the schema and the geo
-- reference tables (countries, provinces, cities). The SES catalog cache is also
-- preserved — it's repopulated from the SES API and re-fetching is slow.
--
-- Usage:
--   docker exec -i kasify-postgres psql -U postgres -d kasify < api/reset_db.sql
--
-- TRUNCATE ... CASCADE handles FK dependencies in one shot. Tables are listed
-- explicitly (rather than relying on cascades from `accounts` alone) so the
-- intent of the script is obvious at code review and a stray `ON DELETE CASCADE`
-- removal can't silently leave a table populated.

BEGIN;

TRUNCATE TABLE
    api_tokens,
    crew_invitations,
    property_access,
    property_channel_integrations,
    property_nuki_smartlocks,
    property_unifi_smartlocks,
    property_smartlocks,
    booking_access_codes,
    guests,
    tasks,
    files,
    bookings,
    calendar_events,
    properties,
    account_nuki_integrations,
    account_unifi_integrations,
    account_ses_hospedajes_integrations,
    account_integrations,
    users,
    accounts
RESTART IDENTITY CASCADE;

COMMIT;
