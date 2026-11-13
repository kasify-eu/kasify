export type BookingsResponse = {
  bookings: Booking[];
  total: number;
};

// Generic, channel-agnostic payment types. These match the backend `payment_type` enum values
// verbatim; SES-specific codes are mapped server-side. Labels are localized via the
// `bookingDetails.paymentTypes.<value>` i18n keys.
export const PAYMENT_TYPES = [
  "platform",
  "cash",
  "credit_card",
  "bank_transfer",
  "mobile",
  "pay_at_property",
  "gift_card",
  "other",
] as const;

export type PaymentType = (typeof PAYMENT_TYPES)[number];

export const DEFAULT_PAYMENT_TYPE: PaymentType = "platform";

// Channel values match the backend BookingChannel enum names.
export const BOOKING_CHANNELS = [
  { value: "Direct", label: "Direct" },
  { value: "Airbnb", label: "Airbnb" },
  { value: "Booking", label: "Booking.com" },
  { value: "Vrbo", label: "VRBO" },
  { value: "Google", label: "Google" },
] as const;

export type BookingChannel = (typeof BOOKING_CHANNELS)[number]["value"];

export const DEFAULT_BOOKING_CHANNEL: BookingChannel = "Direct";

export type CreateBookingRequest = {
    guest_first_name: string;
    guest_last_name?: string;
    guest_email?: string;
    guest_phone?: string;
    from: string;
    to: string;
    adults_count?: number;
    children_count?: number;
    infants_count?: number;
    payment_type?: PaymentType;
    channel?: BookingChannel;
    channel_id?: string;
    notes?: string;
};

export type AccessCode = {
  id: string;
  access_code: string;
  smartlock_id: string;
  smartlock_name: string;
};

type Country = {
  id: string;
  name: string;
  name_es: string;
  alpha_2: string;
  alpha_3: string;
};

type Province = {
  id: string;
  country_id: string;
  name: string;
  code_iso_3166_2: string;
  ine_code_pro: string;
};

type City = {
  id: string;
  country_id: string;
  province_id: string;
  name: string;
  ine_code: string;
  ine_code_mun: string;
};

export type Guest = {
  id: string;
  booking_id: string;
  property_id: string;
  first_name: string;
  last_name: string;
  second_last_name?: string | null;
  nationality?: Country | null;
  date_of_birth?: string | null;
  sex?: string | null;
  residence_country?: Country | null;
  residence_province?: Province | null;
  residence_city?: City | null;
  residence_city_name?: string | null;
  residence_address?: string | null;
  residence_postal_code?: string | null;
  document_type?: string | null;
  document_number?: string | null;
  document_support_number?: string | null;
  email?: string | null;
  phone?: string | null;
  phone_prefix?: string | null;
  relationship_code?: string | null;
  // Booking-level context, populated only on the guests list endpoint.
  channel?: string | null;
  channel_id?: string | null;
  // Owner-private per-stay review; absent for managers of shared properties.
  would_host_again?: boolean | null;
  // Effective email-marketing permission: opted in at check-in and not unsubscribed since.
  marketing_consent?: boolean;
  // Raw opt-in timestamp; set with marketing_consent=false means the guest unsubscribed.
  marketing_consent_at?: string | null;
};

type BookingBase = {
  id: string;
  property: import("~/types/properties").Property;
  from: string;
  to: string;
  channel: string | null;
  status: string;
  guest_first_name: string | null;
  adults_count: number | null;
  children_count: number | null;
  infants_count: number | null;
  arrival_time: string | null;
  departure_time: string | null;
  checkin_time: string | null;
  checkout_time: string | null;
    created_at: string;
};

export type Booking = BookingBase & {
    channel_id: string | null;
    checkin_code: string | null;
    payment_type: PaymentType;
    notes: string | null;
    guest_last_name: string | null;
    guest_email: string | null;
    guest_phone: string | null;
    special_requests: string | null;
    // Owner's private per-stay guest review — owner-only, absent for crew/managers.
    would_host_again?: boolean | null;
    guest_review_notes?: string | null;
  access_code: string | null;
  checkin_completed_at: string | null;
  ses_communications: import("~/types/ses-monitoring").SesCommunicationDto[];
  access_codes: AccessCode[];
  guests?: Guest[];
  cleaning_task?: import("~/types/tasks").Task | null;
};
