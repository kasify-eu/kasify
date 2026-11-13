import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import type { AdminSesBookingDetailResponse } from "~/types/ses-monitoring";

export type AdminSesActionResult = {
  ok: boolean;
  rejected: boolean;
  data: AdminSesBookingDetailResponse | null;
  bookingId: string;
  intent: string;
  error: string | null;
  code: string | null;
};

/**
 * Server-action handler for the admin SES operations (submit PV/RH alta, anular, poll).
 * The SES details drawer posts to the route it is rendered on, so any route hosting it
 * must delegate its `action` here.
 */
export async function handleAdminSesAction(
  request: Request,
): Promise<AdminSesActionResult> {
  const formData = await request.formData();
  const intent = String(formData.get("intent") ?? "");
  const bookingId = String(formData.get("booking_id") ?? "");
  const communicationId = String(formData.get("communication_id") ?? "");

  const base: AdminSesActionResult = {
    ok: false,
    rejected: false,
    data: null,
    bookingId,
    intent,
    error: null,
    code: null,
  };

  if (!bookingId || !["submit", "submit-rh", "cancel", "poll"].includes(intent)) {
    return { ...base, error: "Invalid action" };
  }

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const response =
      intent === "submit"
        ? await apiClient.submitAdminSesBooking(bookingId)
        : intent === "submit-rh"
          ? await apiClient.submitAdminSesBookingRh(bookingId)
          : intent === "poll"
            ? await apiClient.pollAdminSesCommunication(communicationId)
            : await apiClient.cancelAdminSesCommunication(communicationId);
    // status 200 = accepted by SES; 202 = SES rejected (still informative).
    return {
      ...base,
      ok: response.status === 200,
      rejected: response.status === 202,
      data: response.data,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return { ...base, error: apiError.message, code: apiError.code ?? null };
  }
}
