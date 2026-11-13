export type CalendarChannel =
  | "airbnb"
  | "booking"
  | "direct"
  | "vrbo"
  | "google"
  | "block"
  | "unknown";

/**
 * Task kinds. `unknown` is used when the backend redacted task details (the
 * caller is a non-manager and the task isn't assigned to them) — the slot
 * still renders, but with neutral styling and a generic label.
 */
export type CalendarTaskKind =
  | "cleaning"
  | "maintenance"
  | "pool"
  | "gardening"
  | "unknown";

export interface CalendarBooking {
  /** Booking ID — used to open the booking details panel. */
  id: string;
  guest: string;
  channel: CalendarChannel;
  /** ISO datetime — inclusive check-in. Time portion drives fractional column positioning. */
  in: string;
  /** ISO datetime — inclusive check-out. Time portion drives fractional column positioning. */
  out: string;
  code: string;
  kind?: "booking" | "block";
}

interface CalendarTaskBase {
  id: string;
  kind: CalendarTaskKind;
  title: string;
}

export interface CalendarSingleDayTask extends CalendarTaskBase {
  /** ISO date YYYY-MM-DD */
  date: string;
  span?: false;
}

export interface CalendarMultiDayTask extends CalendarTaskBase {
  /** ISO datetime — inclusive start. Time portion drives fractional column positioning. */
  start: string;
  /** ISO datetime — inclusive end. Time portion drives fractional column positioning. */
  end: string;
  span: true;
}

export type CalendarTask = CalendarSingleDayTask | CalendarMultiDayTask;

/**
 * One row in the timeline: property identity plus its bookings/tasks for the
 * current window. Mirrors the API's per-property grouping so no global
 * flattening/regrouping is needed.
 */
export interface CalendarRow {
  id: string;
  name: string;
  bookings: CalendarBooking[];
  tasks: CalendarTask[];
}

/**
 * UI filter key for calendar views: a mix of the booking discriminator and
 * individual task kinds, so the user can toggle "show bookings", "show
 * cleaning tasks", "show maintenance tasks", etc. independently.
 */
export type CalendarFilterKey = "booking" | CalendarTaskKind;

export type CalendarPickedEvent =
  | { type: "booking"; booking: CalendarBooking }
  | { type: "task"; task: CalendarTask };
