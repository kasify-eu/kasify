export type NukiIntegration = {
  id: string;
};

export type SesHospedajesIntegration = {
  id: string;
  arrendador_code: string;
  ses_username: string;
  enabled: boolean;
  rh_direct_bookings_enabled: boolean;
  // Set when SES rejected a call with a credential-family código (10103/10107/10119/10120).
  // While set, guest-registry submissions for the account are paused; re-saving verified
  // credentials clears it. Null/undefined = credentials believed valid.
  credentials_error_code?: number | null;
  credentials_error_message?: string | null;
  credentials_error_at?: string | null;
};

export type CreateSesHospedajesIntegrationRequest = {
  arrendador_code: string;
  ses_username: string;
  ses_password: string;
  // Optional: omit on update to leave the stored value untouched; defaults to true on create.
  rh_direct_bookings_enabled?: boolean;
};

// Lightweight settings update that does not touch credentials (PATCH .../settings).
export type UpdateSesHospedajesSettingsRequest = {
  rh_direct_bookings_enabled: boolean;
};

export type UnifiIntegration = {
  id: string;
  unifi_username: string;
  unifi_console_url: string;
};

export type UnifiIntegrationRequest = {
  unifi_username: string;
  unifi_password: string;
  unifi_console_url: string;
};

type NukiIntegrationItem = {
  type: "nuki";
  id: string;
};

type UnifiIntegrationItem = {
  type: "unifi";
  id: string;
  unifi_username: string;
  unifi_console_url: string;
};

type SesHospedajesIntegrationItem = {
  type: "ses_hospedajes";
  id: string;
  arrendador_code: string;
  ses_username: string;
  enabled: boolean;
  rh_direct_bookings_enabled: boolean;
};

type IntegrationItem =
  | NukiIntegrationItem
  | UnifiIntegrationItem
  | SesHospedajesIntegrationItem;

export type IntegrationsResponse = {
  integrations: IntegrationItem[];
};

type AssignedProperty = {
  property_id: string;
  property_name: string;
  smartlock_record_id: string;
};

export type AvailableNukiSmartlock = {
  smartlock_id: number;
  name: string;
  lock_state?: number;
  battery_charge?: number;
  battery_critical: boolean;
  offline?: boolean;
  assigned_property?: AssignedProperty;
};

export type NukiStatus = "online" | "unauthorized" | "unreachable";

export type NukiSmartlocksResponse = {
  smartlocks: AvailableNukiSmartlock[];
  nuki_status: NukiStatus;
};

export type AssignSmartlockRequest = {
  name: string;
  nuki_smartlock_id: string;
  nuki_integration_id: string;
};

export type AssignUnifiSmartlockRequest = {
  name: string;
  unifi_floor_id: string;
  unifi_door_id: string;
};

export type AvailableUnifiSmartlock = {
  floor_id: string;
  door_id: string;
  name: string;
  lock_state?: string;
  assigned_property?: AssignedProperty;
};

export type UnifiStatus = "online" | "unauthorized" | "unreachable";

export type UnifiSmartlocksResponse = {
  smartlocks: AvailableUnifiSmartlock[];
  unifi_status: UnifiStatus;
};
