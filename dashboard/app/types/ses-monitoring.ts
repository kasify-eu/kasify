import type { Booking } from "~/types/bookings";

export type SesHospedajesSubmission = {
  id: string;
  booking_id: string;
  operation: string;
  attempted_at: string;
  respuesta_codigo: number | null;
  respuesta_descripcion: string | null;
  ses_lote: string | null;
  ses_estado: string | null;
  ses_estado_desc: string | null;
  error_text: string | null;
  ses_communication_id: string | null;
  response_xml: string | null;
};

export type SesCommunicationType = "pv" | "rh";
export type SesCommunicationOperation = "alta" | "anulacion";
export type SesCommunicationStatus =
  | "submitted"
  | "processing"
  | "accepted"
  | "rejected"
  | "failed";

export type SesCommunicationDto = {
  id: string;
  type: SesCommunicationType;
  operation: SesCommunicationOperation;
  status: SesCommunicationStatus;
  lote: string | null;
  codigo_comunicacion: string | null;
  estado: string | null;
  estado_desc: string | null;
  submitted_at: string | null;
  last_attempt_at: string | null;
  last_error: string | null;
};

/** Per-type status derived from the alta/anulación comm rows for the booking UI. */
export type SesCommunicationUiStatus =
  | "submitted"
  | "processing"
  | "success"
  | "rejected"
  | "error"
  | "cancelling"
  | "cancelled";

/**
 * Derive the UI status for one communication type from a booking's comm rows.
 * An accepted anulación → "cancelled"; any other anulación row → "cancelling" (in flight or
 * retrying); otherwise map the alta status. Returns null when no comm of that type exists.
 */
export function communicationStatus(
  comms: SesCommunicationDto[] | null | undefined,
  type: SesCommunicationType,
): SesCommunicationUiStatus | null {
  const ofType = (comms ?? []).filter((c) => c.type === type);
  // At most one row per (type, operation) exists (DB unique constraint), so first is the row.
  const anulacion = ofType.find((c) => c.operation === "anulacion");
  const alta = ofType.find((c) => c.operation === "alta");

  if (anulacion?.status === "accepted") return "cancelled";
  if (anulacion) return "cancelling";
  if (!alta) return null;
  switch (alta.status) {
    case "submitted":
      return "submitted";
    case "processing":
      return "processing";
    case "accepted":
      return "success";
    case "rejected":
      return "rejected";
    case "failed":
      return "error";
    default:
      return null;
  }
}

export type AdminSesBookingDetailResponse = {
  booking: Booking;
  submissions: SesHospedajesSubmission[];
};
