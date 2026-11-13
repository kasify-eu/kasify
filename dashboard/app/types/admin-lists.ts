import type { Booking, Guest } from "~/types/bookings";
import type { User } from "~/types/user";

// ============================================
// Admin Users
// ============================================

export type AdminUsersPageResponse = {
  items: User[];
  page: number;
  size: number;
  total: number;
};

export type AdminUsersFilters = {
  page?: number;
  size?: number;
  query?: string;
};

// ============================================
// Admin Properties
// ============================================

export type AdminPropertyOwner = {
  id: string;
  name: string;
  last_name: string;
  email: string;
};

// Mirrors the backend `Calendar` DTO (camelCase, no @SerialName) — one channel
// integration (Airbnb/Booking iCal feed) with its sync status.
export type AdminCalendar = {
  channel: string;
  url: string;
  syncIntervalMinutes: number;
  lastSyncAt: string | null;
  lastError: string | null;
};

export type AdminProperty = {
  id: string;
  account_id: string;
  name: string;
  address: string;
  city: string;
  zip_code: string;
  rooms_count: number;
  has_internet: boolean | null;
  ses_establishment_code: string | null;
  cover_image_url: string | null;
  created_at: string;
  owner: AdminPropertyOwner;
  calendars: AdminCalendar[];
  // Distinct vendors of the property's configured smart locks ("Nuki", "Unifi", ...)
  smartlock_vendors: string[];
};

// Tri-state integration filter accepted by GET /admin/properties (absent = any)
export type IntegrationConfiguredFilter = "configured" | "not_configured";

export type AdminPropertiesPageResponse = {
  items: AdminProperty[];
  page: number;
  size: number;
  total: number;
};

export type AdminPropertiesFilters = {
  page?: number;
  size?: number;
  query?: string;
  property_id?: string;
  ses?: IntegrationConfiguredFilter;
  nuki?: IntegrationConfiguredFilter;
  unifi?: IntegrationConfiguredFilter;
};

// ============================================
// Admin Bookings
// ============================================

export type AdminBookingsPageResponse = {
  items: Booking[];
  page: number;
  size: number;
  total: number;
};

export type AdminBookingsFilters = {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
  property_id?: string;
  query?: string;
  channel?: string;
};

// ============================================
// Admin Guests
// ============================================

export type AdminGuest = {
  id: string;
  booking_id: string;
  property_id: string;
  property_name: string;
  property_city: string;
  channel_id: string | null;
  owner_name: string;
  first_name: string;
  last_name: string;
  second_last_name: string | null;
  nationality_alpha_2: string | null;
  nationality_name: string | null;
  date_of_birth: string | null;
  document_type: string | null;
  document_number: string | null;
  email: string | null;
  phone: string | null;
  phone_prefix: string | null;
};

export type AdminGuestsPageResponse = {
  items: AdminGuest[];
  page: number;
  size: number;
  total: number;
};

/**
 * Adapts a flattened {@link AdminGuest} list row into the nested {@link Guest}
 * shape expected by `GuestDetailsDrawer`. There is no single-admin-guest fetch
 * endpoint, so the drawer is populated entirely from the row data — fields the
 * admin list doesn't carry (residence, sex, document support, relationship) are
 * left null. Nationality is reconstructed as a minimal Country object from the
 * `alpha_2` / `name` fields the row provides; the remaining Country fields are
 * unavailable here, so they default to empty/derived values.
 */
export function adminGuestToGuest(g: AdminGuest): Guest {
  const nationality =
    g.nationality_alpha_2 || g.nationality_name
      ? {
          id: "",
          name: g.nationality_name ?? "",
          name_es: g.nationality_name ?? "",
          alpha_2: g.nationality_alpha_2 ?? "",
          alpha_3: "",
        }
      : null;

  return {
    id: g.id,
    booking_id: g.booking_id,
    property_id: g.property_id,
    first_name: g.first_name,
    last_name: g.last_name,
    second_last_name: g.second_last_name,
    nationality,
    date_of_birth: g.date_of_birth,
    sex: null,
    residence_country: null,
    residence_province: null,
    residence_city: null,
    residence_city_name: null,
    residence_address: null,
    residence_postal_code: null,
    document_type: g.document_type,
    document_number: g.document_number,
    document_support_number: null,
    email: g.email,
    phone: g.phone,
    phone_prefix: g.phone_prefix,
    relationship_code: null,
  };
}

export type AdminGuestsFilters = {
  page?: number;
  size?: number;
  property_id?: string;
  channel_id?: string;
  query?: string;
};
