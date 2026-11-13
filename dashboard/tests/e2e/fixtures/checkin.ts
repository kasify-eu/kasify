import type { CheckinResponse } from "../../../app/types/checkin";

const baseProperty = {
  name: "Casa Olivo",
  address: "Calle Mayor 12",
  city: "Madrid",
  zip_code: "28013",
  cover_image_url: null,
};

function base(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return {
    property: baseProperty,
    from: "2026-06-10",
    to: "2026-06-14",
    guests_number: 2,
    booking_code: "TEST",
    guests: [],
    checkin_completed: false,
    checkin_closed: false,
    has_minors: false,
    ...overrides,
  };
}

export function activeCheckin(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return base({ booking_code: "ACTIVE", ...overrides });
}

export function completedCheckin(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return base({
    booking_code: "COMPLETED",
    checkin_completed: true,
    guests: [
      {
        id: "g1",
        first_name: "Ana",
        last_name: "García",
      },
      {
        id: "g2",
        first_name: "Luis",
        last_name: "Martín",
      },
    ],
    ...overrides,
  });
}

export function closedCheckin(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return base({ booking_code: "CLOSED", checkin_closed: true, ...overrides });
}

// Booking that still has minors to register: one adult already saved, two
// guests remaining. Drives the parentesco section and the "Guest 2 of 3"
// step counter.
export function minorsCheckin(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return base({
    booking_code: "MINORS",
    guests_number: 3,
    has_minors: true,
    guests: [{ id: "g1", first_name: "Ana", last_name: "García" }],
    ...overrides,
  });
}

// Single-guest booking: registering the only guest completes the check-in,
// so the guest POST returns checkin_completed: true.
export function lastGuestCheckin(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return base({ booking_code: "LASTGUEST", guests_number: 1, ...overrides });
}

// Booking whose guest POST always fails with a duplicate-document conflict.
export function dupDocCheckin(overrides: Partial<CheckinResponse> = {}): CheckinResponse {
  return base({ booking_code: "DUPDOC", ...overrides });
}
