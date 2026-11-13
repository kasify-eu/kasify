type CheckinPropertyInfo = {
  name: string;
  address: string;
  city: string;
  zip_code: string;
  cover_image_url?: string | null;
};

// Mirrors api/src/main/kotlin/model/api/CheckinGuest.kt — the public
// check-in snapshot is redacted to exactly these fields.
export type CheckinGuest = {
  id: string;
  first_name: string;
  last_name: string;
};

export type CheckinResponse = {
  property: CheckinPropertyInfo;
  from: string;
  to: string;
  guests_number: number;
  booking_code: string;
  guests: CheckinGuest[];
  checkin_completed: boolean;
  checkin_closed: boolean;
  // Booking-derived hint: true when children/infants > 0. Used by the form to
  // ask accompanying adults to declare their "parentesco" to the minor(s).
  has_minors: boolean;
};

type GuestDocumentType = "DNI" | "NIE" | "Passport" | "Other";
type GuestSex = "Male" | "Female";

export type GuestRegistrationRequest = {
  first_name: string;
  last_name: string;
  second_last_name?: string | null;
  nationality_country_code: string; // ISO 3166-1 alpha-3
  date_of_birth: string; // ISO yyyy-MM-dd
  sex: GuestSex;
  document_type?: GuestDocumentType | null;
  document_number?: string | null;
  document_support_number?: string | null;
  residence_address?: string | null;
  residence_country_code?: string | null; // ISO 3166-1 alpha-3
  residence_province_id?: string | null; // Required for Spanish residence
  residence_city_id?: string | null; // Required for Spanish residence
  residence_city?: string | null; // Free-text city for non-Spanish residence
  residence_postal_code?: string | null;
  phone_prefix?: string | null;
  phone?: string | null;
  email?: string | null;
  // SES parentesco code. Required for adult guests (>= 18) when the booking
  // has minors; never required for minors themselves.
  relationship_code?: string | null;
  // Optional marketing opt-in (GDPR consent). The backend only records it for
  // adults (18+) who provided an email.
  marketing_consent?: boolean;
};

// Confirmation returned by POST /marketing/unsubscribe/{token}. The email is
// masked because the link authenticates by token alone.
export type MarketingUnsubscribeResponse = {
  email_masked: string;
};
