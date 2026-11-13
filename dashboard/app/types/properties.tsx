type Calendar = {
  channel: string;
  url: string;
  syncIntervalMinutes: number;
  lastSyncAt: string | null;
  lastError: string | null;
};

type NukiSmartLockInfo = {
  id: string;
  nuki_smartlock_id: string;
};

type UnifiSmartLockInfo = {
  id: string;
  resource_id: string;
  schedule_id: string;
};

export type SmartLockInfo = {
  id: string;
  vendor: string;
  name: string;
};

export type SmartLock = {
  id: string;
  property_id: string;
  vendor: string;
  name: string;
  nuki_smartlock?: NukiSmartLockInfo;
  unifi_smartlock?: UnifiSmartLockInfo;
  lock_state?: number;
  battery_level?: number;
  battery_charging?: boolean;
  offline?: boolean;
};

export type PropertyMember = {
    id: string;
    name: string;
    last_name: string;
    email: string;
    service: string | null;
    access_level: string;
};

export enum PropertyAccessLevel {
  Manager = "manager",
  Staff = "staff",
  Contractor = "contractor",
  Owner = "owner",
}

// Owners and managers; a missing role also counts as manageable (own property).
export function isManagerOrAbove(
  role: PropertyAccessLevel | null | undefined,
): boolean {
  return (
    role !== PropertyAccessLevel.Staff && role !== PropertyAccessLevel.Contractor
  );
}

export type Property = {
  id: string;
  accountId?: string;
  name: string;
  role?: PropertyAccessLevel | null;
  default_cleaner?: PropertyMember;
  smart_locks?: SmartLockInfo[];
  calendars?: Calendar[];
  team?: PropertyMember[];
  description?: string;
  address?: string;
  city?: string;
  zip_code?: string;
  country?: string;
  country_id?: string | null;
  country_code?: string;
  province_id?: string | null;
  city_id?: string | null;
  airbnb_ical_url?: string;
  booking_ical_url?: string;
  cover_image_url?: string | null;
  has_internet?: boolean | null;
  rooms_count?: number;
  checkin_time?: string;
  checkout_time?: string;
  ses_establishment_code?: string | null;
  // The SES integration assigned to file for this property; null/undefined = not configured.
  // One integration per property: configuring it on a second integration is rejected
  // (ses_hospedajes.property_already_configured) until the code is cleared on the first.
  ses_integration_id?: string | null;
  ses_establishment_type?: string | null;
  /** NIF/CIF of the actual arrendador (may differ from the account holder). */
  arrendador_nif?: string | null;
  /** Account has an enabled SES integration AND the property has an establishment code. */
  ses_enabled?: boolean | null;
  /**
   * Derived SES filing health. `paused_config` = SES rejected the establishment code (fix and
   * re-save it); `paused_credentials` = the assigned integration's WS credentials are flagged.
   * Only present on list/detail responses (like ses_enabled).
   */
  ses_status?: SesPropertyStatus | null;
  /** SES's own error text when ses_status is `paused_config`; cleared when the code is re-saved. */
  ses_config_error_message?: string | null;
};

export type SesPropertyStatus =
  | "not_configured"
  | "disabled"
  | "paused_credentials"
  | "paused_config"
  | "ok";

export type CreatePropertyRequest = {
  name: string;
  description?: string;
  address: string;
  city?: string;
  zip_code: string;
  airbnb_ical_url?: string;
  booking_ical_url?: string;
  country_code?: string;
  province_id?: string;
  city_id?: string;
  has_internet?: boolean;
  rooms_count?: number;
};

export type UpdatePropertyRequest = {
  name?: string;
  description?: string;
  address?: string;
  city?: string;
  zip_code?: string;
  country?: string;
  country_code?: string;
  province_id?: string;
  city_id?: string;
  airbnb_ical_url?: string;
  booking_ical_url?: string;
  default_cleaner_id?: string;
  checkin_time?: string;
  checkout_time?: string;
  ses_establishment_code?: string;
  ses_integration_id?: string;
  arrendador_nif?: string;
  has_internet?: boolean;
  rooms_count?: number;
};

/**
 * Partial update body for PATCH /properties/{id}. Only supplied fields are applied;
 * absent fields are left untouched. Geo fields and `airbnb_ical_url` are excluded —
 * use PUT (`updateProperty`) for those.
 */
export type PropertyPatch = {
  name?: string;
  address?: string;
  zip_code?: string;
  default_cleaner_id?: string;
  ses_establishment_code?: string;
  // Required when setting a code on an unassigned property; "" code clears code + assignment.
  ses_integration_id?: string;
  ses_establishment_type?: string;
  arrendador_nif?: string;
  has_internet?: boolean;
  rooms_count?: number;
  checkin_time?: string;
  checkout_time?: string;
};

export type PropertiesResponse = {
  properties: Property[];
  total: number;
  page: number;
  size: number;
};

export type SmartLocksStatusResponse = SmartLock[];
