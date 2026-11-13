import type {
  CalendarChannel,
  CalendarTaskKind,
} from "~/components/calendar-timeline/types";

export interface ChannelStyle {
  name: string;
  ink: string;
  bg: string;
  bar: string;
}

export interface TaskStyle {
  ink: string;
  bg: string;
  bar: string;
  label: string;
  short: string;
}

export const CHANNEL_STYLE: Record<CalendarChannel, ChannelStyle> = {
  airbnb: {
    name: "Airbnb",
    ink: "oklch(0.46 0.16 18)",
    bg: "oklch(0.94 0.04 18)",
    bar: "oklch(0.78 0.10 18)",
  },
  booking: {
    name: "Booking",
    ink: "oklch(0.40 0.14 255)",
    bg: "oklch(0.94 0.03 255)",
    bar: "oklch(0.72 0.09 255)",
  },
  direct: {
    name: "Direct",
    ink: "oklch(0.42 0.13 310)",
    bg: "oklch(0.95 0.04 310)",
    bar: "oklch(0.72 0.11 310)",
  },
  vrbo: {
    name: "Vrbo",
    ink: "oklch(0.40 0.13 200)",
    bg: "oklch(0.94 0.04 200)",
    bar: "oklch(0.74 0.09 200)",
  },
  google: {
    name: "Google",
    ink: "oklch(0.42 0.14 145)",
    bg: "oklch(0.95 0.04 145)",
    bar: "oklch(0.70 0.12 145)",
  },
  block: {
    name: "Owner block",
    ink: "oklch(0.40 0.01 270)",
    bg: "oklch(0.92 0.005 270)",
    bar: "oklch(0.62 0.005 270)",
  },
  unknown: {
    name: "Unknown",
    ink: "oklch(0.40 0.04 270)",
    bg: "oklch(0.94 0.01 270)",
    bar: "oklch(0.78 0.02 270)",
  },
};

// Design-system §2.4: one hue per task/activity type, shared by every badge
// surface (tasks list, activities table/cards) so the same entity never
// changes color between screens. Cleaning = amber, maintenance = blue;
// bookings use indigo (the `current_guest` hue) to stay distinct from
// maintenance.
export const TYPE_BADGE_CLASSES: Record<string, string> = {
  booking: "bg-indigo-50 border-indigo-100 text-indigo-700",
  cleaning: "bg-amber-50 border-amber-100 text-amber-700",
  maintenance: "bg-blue-50 border-blue-100 text-blue-700",
  pool: "bg-cyan-50 border-cyan-100 text-cyan-700",
  gardening: "bg-green-50 border-green-100 text-green-700",
  unknown: "bg-gray-100 border-gray-200 text-gray-600",
};

export function typeBadgeClasses(type: string | undefined): string {
  return TYPE_BADGE_CLASSES[type ?? "unknown"] ?? TYPE_BADGE_CLASSES.unknown;
}

export const TASK_STYLE: Record<CalendarTaskKind, TaskStyle> = {
  cleaning: {
    ink: "oklch(0.46 0.13 75)",
    bg: "oklch(0.97 0.05 80)",
    bar: "oklch(0.74 0.13 75)",
    label: "Cleaning",
    short: "CLN",
  },
  maintenance: {
    ink: "oklch(0.42 0.09 30)",
    bg: "oklch(0.96 0.04 30)",
    bar: "oklch(0.68 0.10 30)",
    label: "Maintenance",
    short: "MNT",
  },
  pool: {
    ink: "oklch(0.42 0.13 220)",
    bg: "oklch(0.96 0.04 220)",
    bar: "oklch(0.70 0.11 220)",
    label: "Pool",
    short: "POOL",
  },
  gardening: {
    ink: "oklch(0.42 0.13 145)",
    bg: "oklch(0.96 0.04 145)",
    bar: "oklch(0.68 0.12 145)",
    label: "Gardening",
    short: "GRD",
  },
  unknown: {
    ink: "oklch(0.40 0.04 270)",
    bg: "oklch(0.94 0.01 270)",
    bar: "oklch(0.78 0.02 270)",
    label: "Task",
    short: "TSK",
  },
};
