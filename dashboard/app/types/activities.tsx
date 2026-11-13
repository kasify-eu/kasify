import type { Booking } from "./bookings";

type ActivityType =
  | "booking"
  | "check_in"
  | "check_out"
  | "current_guest"
  | "cleaning"
  | "maintenance";

type TimelineTask = {
  id: string;
  property_name: string | null;
  status: string;
  assigned_to_name: string | null;
  notes: string | null;
};

export type Activity = {
  id: string;
  type: ActivityType;
  date?: string;
  time?: string;
  end_time?: string;
  end_date?: string;
  property_id: string;
  booking?: Booking;
  task?: TimelineTask;
};

export type TimelineGroup = {
  date: string;
  date_offset: number;
  activities: Activity[];
};

// Legacy /timeline response
export type ActivitiesResponse = {
  timeline: TimelineGroup[];
};

// Flat paginated /activities response
export type FlatActivitiesResponse = {
  activities: Activity[];
  page: number;
  size: number;
  total: number;
};
