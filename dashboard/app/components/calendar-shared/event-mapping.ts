import type {
  CalendarBooking,
  CalendarChannel,
  CalendarRow,
  CalendarTask,
  CalendarTaskKind,
} from "~/components/calendar-timeline/types";
import {
  getCalendarBookingGuestCount,
  getCalendarBookingGuestName,
} from "~/types/calendar";
import type { CalendarEvent, PropertyCalendar } from "~/types/calendar";

function bookingGuestLabel(
  booking: NonNullable<CalendarEvent["booking"]>,
  fallback: string,
): string {
  const name = getCalendarBookingGuestName(booking) ?? fallback;
  const count = getCalendarBookingGuestCount(booking);
  return count > 0 ? `${name} (${count})` : name;
}

function eventToBooking(
  e: CalendarEvent,
  fallbackGuestLabel: string,
): CalendarBooking | null {
  if (e.type !== "booking") return null;
  const channel = (e.booking?.channel?.toLowerCase() ?? "unknown") as CalendarChannel;
  return {
    id: e.booking?.id ?? e.id,
    guest: e.booking
      ? bookingGuestLabel(e.booking, fallbackGuestLabel)
      : fallbackGuestLabel,
    channel,
    in: e.from,
    out: e.to,
    code: e.booking?.channelId ?? e.id,
  };
}

function eventToTask(e: CalendarEvent): CalendarTask | null {
  if (e.type !== "task" || !e.task) return null;
  // The backend redacts non-manager foreign tasks to `{ id }` — render those
  // with neutral styling so the slot is still visible.
  const rawKind = e.task.task_type;
  const kind: CalendarTaskKind = rawKind ?? "unknown";
  const id = e.task.id;
  const title = rawKind ? rawKind.charAt(0).toUpperCase() + rawKind.slice(1) : "Task";
  const fromDate = e.from.slice(0, 10);
  const toDate = e.to.slice(0, 10);
  return fromDate === toDate
    ? { id, kind, title, date: fromDate }
    : { id, kind, title, start: e.from, end: e.to, span: true };
}

export function propertyToRow(
  p: PropertyCalendar,
  fallbackGuestLabel: string,
): CalendarRow {
  const bookings: CalendarBooking[] = [];
  const tasks: CalendarTask[] = [];
  for (const e of p.events) {
    const b = eventToBooking(e, fallbackGuestLabel);
    if (b) {
      bookings.push(b);
      continue;
    }
    const t = eventToTask(e);
    if (t) tasks.push(t);
  }
  return { id: p.id, name: p.name, bookings, tasks };
}
