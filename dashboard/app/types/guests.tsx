import type { Guest } from "~/types/bookings";

export type GuestsResponse = {
  guests: Guest[];
  page: number;
  size: number;
  total: number;
};
