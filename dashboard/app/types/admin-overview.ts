/**
 * Platform-wide snapshot for the admin home dashboard (`GET /admin/overview`).
 * Mirrors the backend `AdminOverviewResponse`.
 */
export type CountWithMonthlyDelta = {
  total: number;
  new_this_month: number;
};

export type SesOverview = {
  submissions_sent: number;
  submissions_with_errors: number;
};

export type IntegrationsOverview = {
  nuki_integrations: number;
  unifi_integrations: number;
  booking_codes_created: number;
};

export type AdminOverviewResponse = {
  generated_at: string;
  properties: CountWithMonthlyDelta;
  users: CountWithMonthlyDelta;
  bookings: CountWithMonthlyDelta;
  guests: CountWithMonthlyDelta;
  ses: SesOverview;
  integrations: IntegrationsOverview;
};
