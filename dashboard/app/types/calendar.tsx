/**
 * Mirrors the backend `EventType` enum (serialized as the lowercase name) —
 * a polymorphic discriminator for what the calendar event joins to.
 *   "booking" → booking row attached on the `booking` field
 *   "task"    → task row attached on the `task` field (read `task.task_type` for the kind of work)
 *   "block"   → owner-blocked dates, no related row
 */
type CalendarEventType = "booking" | "task" | "block";

/**
 * Channel the booking originated from. Mirrors the backend `BookingChannel`
 * enum (serialized as the capitalized name).
 */
type CalendarBookingChannel =
  | "Airbnb"
  | "Booking"
  | "Direct"
  | "Vrbo"
  | "Google";

/**
 * Booking information attached to a calendar event. Field visibility is
 * controlled by the backend (`@VisibleTo`): everyone sees the guest first
 * name and counts; managers also see the last name and `channel`; only
 * owners see `channelId`.
 */
export type CalendarEventBooking = {
  id: string;
  channelId?: string;
  guest_first_name?: string;
  guest_last_name?: string;
  adults_count?: number;
  children_count?: number;
  infants_count?: number;
  channel?: CalendarBookingChannel;
};

/**
 * Task information attached to a calendar event. When the caller is a
 * non-manager (`staff`/`contractor`) and the task is not assigned to them,
 * the backend strips every field except `id`.
 */
type CalendarEventTask = {
    id: string;
    task_type?: import("~/types/tasks").TaskType;
    status?: string;
    assigned_to?: string;
    assigned_to_name?: string;
};

/**
 * Base calendar event fields visible to both owners and crew. `booking` is
 * present whenever the event is tied to a booking; the contained fields are
 * filtered by role (see `CalendarEventBooking`).
 */
type CalendarEventBase = {
  id: string;
  propertyId: string;
  from: string;
  to: string;
  type: CalendarEventType;
  createdAt: string;
  updatedAt: string;
    task?: CalendarEventTask;
    booking?: CalendarEventBooking;
};

export type CalendarEvent = CalendarEventBase;

/**
 * One row in the property-grouped calendar response: the property identity
 * and its events for the requested window.
 */
export type PropertyCalendar = {
  id: string;
  name: string;
  events: CalendarEvent[];
};

export type CalendarEventsResponse = {
  properties: PropertyCalendar[];
  from: string;
  to: string;
};

/**
 * Total guest count for a booking event (adults + children + infants).
 */
export function getCalendarBookingGuestCount(
    booking: CalendarEventBooking,
): number {
    return (
        (booking.adults_count ?? 0) +
        (booking.children_count ?? 0) +
        (booking.infants_count ?? 0)
    );
}

/**
 * Display name for a booking guest. Falls back to first name only when the
 * caller doesn't have visibility on the last name (manager+).
 */
export function getCalendarBookingGuestName(
    booking: CalendarEventBooking,
): string | null {
    const first = booking.guest_first_name?.trim();
    const last = booking.guest_last_name?.trim();
    const name = [first, last].filter(Boolean).join(" ");
    return name.length > 0 ? name : null;
}
