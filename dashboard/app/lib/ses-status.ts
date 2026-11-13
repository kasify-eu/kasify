import type { Booking } from "~/types/bookings";
import {
  communicationStatus,
  type SesCommunicationDto,
  type SesCommunicationType,
} from "~/types/ses-monitoring";

/**
 * Coarse SES Hospedajes (PV / Parte de Viajeros) registration status, derived from the
 * booking's `ses_communications` rows. Shared by the activity timeline chip and the booking
 * details panel so both read from a single source of truth.
 */
export type SesState = "sent" | "in_progress" | "error" | "not_sent";

/** The booking's PV (Parte de Viajeros) alta communication, if any. */
export function pvAltaComm(booking: Booking): SesCommunicationDto | null {
  return (
    booking.ses_communications?.find(
      (c) => c.type === "pv" && c.operation === "alta",
    ) ?? null
  );
}

/**
 * Coarse state for one communication type (PV or RH). Returns null when the booking has no
 * communication of that type at all (e.g. non-Spanish properties, or not yet filed), so
 * callers can hide the indicator entirely. A cancelled (annulled) communication is also
 * hidden — there is no active registration to surface.
 */
export function computeSesCommState(
  comms: SesCommunicationDto[] | null | undefined,
  type: SesCommunicationType,
): SesState | null {
  switch (communicationStatus(comms, type)) {
    case "success":
      return "sent";
    case "submitted":
    case "processing":
      return "in_progress";
    case "rejected":
    case "error":
      return "error";
    default:
      return null;
  }
}

/** The booking's PV (Parte de Viajeros) state — see `computeSesCommState`. */
export function computeSesState(booking: Booking): SesState | null {
  return computeSesCommState(booking.ses_communications, "pv");
}

/**
 * The PV alta's `submitted_at` when the parte has actually been filed (submitted/processing/
 * accepted), else null. Mirrors the backend retention guard (`SesCommunicationsRepository
 * .isPvAltaFiled`): once non-null, owner-side guest edits/deletes are locked.
 */
export function sesLockSubmittedAt(booking: Booking): string | null {
  const alta = pvAltaComm(booking);
  if (alta == null) return null;
  const filed =
    alta.status === "submitted" ||
    alta.status === "processing" ||
    alta.status === "accepted";
  return filed ? alta.submitted_at : null;
}

/** i18n key for each state's short chip label (shared en/es entries). */
export const SES_STATE_LABEL_KEY: Record<SesState, string> = {
  sent: "timeline.sesSent",
  in_progress: "timeline.sesInProgress",
  error: "timeline.sesError",
  not_sent: "timeline.sesNotSent",
};
