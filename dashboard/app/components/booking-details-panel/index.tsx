import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { showSuccessToast, showErrorToast, showWarningToast } from "~/lib/toast";
import { sesLockSubmittedAt } from "~/lib/ses-status";
import { Alert } from "@mantine/core";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import { type Booking, DEFAULT_PAYMENT_TYPE } from "~/types/bookings";
import type { GuestRegistrationRequest } from "~/types/checkin";
import type { SmartLockInfo } from "~/types/properties";
import { PropertyAccessLevel } from "~/types/properties";
import { DetailsDrawer } from "~/components/details-drawer/details-drawer";
import { GuestDetailsDrawer, type GuestDetailsDrawerMode } from "~/components/guest-details-drawer";
import { AccessCodesSection } from "./AccessCodesSection";
import { BookingInfoSection } from "./BookingInfoSection";
import { BookingPanelHeader } from "./BookingPanelHeader";
import { CheckinSection } from "./CheckinSection";
import { CleaningTaskSection } from "./CleaningTaskSection";
import { DeleteAccessCodeModal } from "./DeleteAccessCodeModal";
import { GuestDetailsForm } from "./GuestDetailsForm";
import { GuestStaySummarySection, type StaySummaryData } from "./GuestStaySummarySection";
import { useBookingDetails } from "./useBookingDetails";

function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function formatLongDate(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

interface BookingDetailsPanelProps {
  bookingId: string | null;
  opened: boolean;
  onClose: () => void;
  onUpdate?: () => void;
  /**
   * Platform-admin mode: fetch the booking from the admin endpoint and render
   * read-only (no property side-load, so the edit/access-code sections stay
   * hidden). Used by the admin bookings list.
   */
  admin?: boolean;
}

export function BookingDetailsPanel({
  bookingId,
  opened,
  onClose,
  onUpdate,
  admin = false,
}: BookingDetailsPanelProps) {
  const { t } = useTranslation();
  const {
    booking,
    setBooking,
    loading,
    error,
    team,
    smartLocks,
    propertyRole,
    defaultCleanerId,
    refetch,
  } = useBookingDetails(bookingId, opened, admin);

  const [actionLoading, setActionLoading] = useState(false);
  const [accessCodeToDelete, setAccessCodeToDelete] = useState<string | null>(null);
  const [editingTimes, setEditingTimes] = useState(false);
  const [selectedGuestId, setSelectedGuestId] = useState<string | null>(null);
  const [guestDrawerMode, setGuestDrawerMode] = useState<GuestDetailsDrawerMode>("view");
  const [pendingSlotIndex, setPendingSlotIndex] = useState<number | null>(null);
  const [guestSaveError, setGuestSaveError] = useState<string | null>(null);
  const [guestSaving, setGuestSaving] = useState(false);

  const guestDrawerOpened = selectedGuestId !== null || pendingSlotIndex !== null;

  // Look up the selected guest from the already-loaded booking — the API has
  // no `GET /guests/{id}` endpoint yet, so we re-use the registered-guests
  // payload that comes back with the booking.
  const selectedGuest = useMemo(() => {
    if (!selectedGuestId || !booking?.guests) return null;
    return booking.guests.find((g) => g.id === selectedGuestId) ?? null;
  }, [selectedGuestId, booking]);

  // Clear the inner guest drawer when the booking drawer itself closes, so it
  // doesn't briefly re-appear next time the booking drawer is opened.
  useEffect(() => {
    if (!opened) {
      setSelectedGuestId(null);
      setPendingSlotIndex(null);
      setGuestDrawerMode("view");
      setGuestSaveError(null);
    }
  }, [opened]);

  const apiClient = useMemo(
    () => ApiClient.createFromBrowser(createApiConfig()),
    [],
  );

  const guestInitialData = useMemo(
    () => ({
      firstName: booking?.guest_first_name || "",
      lastName: booking?.guest_last_name || "",
      email: booking?.guest_email || "",
      phone: booking?.guest_phone || "",
      adults: booking?.adults_count || 0,
      children: booking?.children_count || 0,
      infants: booking?.infants_count || 0,
      paymentType: booking?.payment_type || DEFAULT_PAYMENT_TYPE,
      specialRequests: booking?.special_requests || "",
    }),
    [booking],
  );

  const staySummaryInitialData = useMemo<StaySummaryData>(
    () => ({
      wouldHostAgain: booking?.would_host_again ?? null,
      reviewNotes: booking?.guest_review_notes || "",
    }),
    [booking],
  );

  // Per CLAUDE.md, never show the raw English err.message: translate the stable
  // error code when a translation exists, else fall back to a generic localized key.
  const translateApiError = (err: unknown, fallbackKey: string): string => {
    const code = err instanceof ApiClientError ? err.code : null;
    const key = code ? `bookingDetails.error.${code}` : null;
    return key && t(key) !== key ? t(key) : t(fallbackKey);
  };

  const handleCreateAccessCode = async () => {
    if (!bookingId) return;
    setActionLoading(true);
    try {
      const createResponse = await apiClient.post<{
        accessCode: string;
        failedSmartlocks: SmartLockInfo[];
      }>(`/bookings/${bookingId}/accesscode`, {});
      await refetch();

      const failedLocks = createResponse.data?.failedSmartlocks ?? [];
      if (failedLocks.length === 0) {
        showSuccessToast(t("bookingDetails.toast.accessCodesCreated"));
      } else if ((booking?.access_codes?.length ?? 0) > 0) {
        showWarningToast(
          t("bookingDetails.toast.accessCodesPartiallyCreated", {
            names: failedLocks.map((l) => l.name).join(", "),
          }),
        );
      } else {
        showErrorToast(t("bookingDetails.toast.accessCodesAllFailed"));
      }
    } catch (err) {
      showErrorToast(
        translateApiError(err, "bookingDetails.toast.accessCodeCreateError"),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleGuestDetailsSave = async (data: typeof guestInitialData) => {
    if (!bookingId) return;
    setActionLoading(true);
    try {
      const response = await apiClient.patch<Booking>(`/bookings/${bookingId}`, {
        guest_first_name: data.firstName,
        guest_last_name: data.lastName,
        // Send the raw string ("" clears one contact while the other remains); the backend's
        // ?.let update ignores null, so "" is how we express "cleared".
        guest_email: data.email.trim(),
        guest_phone: data.phone.trim(),
        adults_count: data.adults,
        children_count: data.children,
        infants_count: data.infants,
        payment_type: data.paymentType,
        special_requests: data.specialRequests,
      });
      setBooking(response.data);
      showSuccessToast(t("bookingDetails.toast.guestUpdated"));
      onUpdate?.();
    } catch (err) {
      showErrorToast(
        translateApiError(err, "bookingDetails.toast.guestUpdateError"),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleSaveStayTimes = async (
    checkinTime: string | null,
    checkoutTime: string | null,
  ) => {
    if (!bookingId) return;
    setActionLoading(true);
    try {
      const response = await apiClient.patch<Booking>(`/bookings/${bookingId}`, {
        checkin_time: checkinTime,
        checkout_time: checkoutTime,
      });
      setBooking(response.data);
      showSuccessToast(t("bookingDetails.toast.timeUpdated"));
      setEditingTimes(false);
      onUpdate?.();
    } catch (err) {
      showErrorToast(
        translateApiError(err, "bookingDetails.toast.timeUpdateError"),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleSaveStaySummary = async (data: StaySummaryData) => {
    if (!bookingId) return;
    setActionLoading(true);
    try {
      const response = await apiClient.patch<Booking>(`/bookings/${bookingId}`, {
        // null means "still unreviewed" — the backend's ?.let update skips it.
        would_host_again: data.wouldHostAgain,
        // "" is how we express "cleared", same as the guest contact fields above.
        guest_review_notes: data.reviewNotes,
      });
      setBooking(response.data);
      showSuccessToast(t("bookingDetails.toast.staySummaryUpdated"));
      onUpdate?.();
    } catch (err) {
      showErrorToast(
        translateApiError(err, "bookingDetails.toast.staySummaryError"),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteAccessCode = async () => {
    if (!bookingId || !accessCodeToDelete) return;
    setActionLoading(true);
    try {
      await apiClient.delete(`/accesscodes/${accessCodeToDelete}`);
      await refetch();
      showSuccessToast(t("bookingDetails.toast.accessCodeDeleted"));
      onUpdate?.();
      setAccessCodeToDelete(null);
    } catch (err) {
      showErrorToast(
        translateApiError(err, "bookingDetails.toast.accessCodeDeleteError"),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const dateInfo = useMemo(() => {
    if (!booking) {
      return {
        checkInShort: "",
        checkOutShort: "",
        totalNights: 0,
        createdAtLong: "",
      };
    }
    const fromDate = new Date(booking.from);
    const toDate = new Date(booking.to);
    const nights = Math.ceil(
      (toDate.getTime() - fromDate.getTime()) / (1000 * 60 * 60 * 24),
    );
    return {
      checkInShort: formatShortDate(booking.from),
      checkOutShort: formatShortDate(booking.to),
      totalNights: nights,
      createdAtLong: booking.created_at ? formatLongDate(booking.created_at) : "",
    };
  }, [booking]);

  const showsCheckinSection =
    propertyRole === PropertyAccessLevel.Owner ||
    propertyRole === PropertyAccessLevel.Manager;
  // The stay-summary fields are owner-only end to end: the API strips them from
  // the payload and rejects PATCHes for everyone else, so don't render the
  // section at all for other roles (or in admin mode).
  const showStaySummary = propertyRole === PropertyAccessLevel.Owner && !admin;
  // Admins get the registered-guest / SES section too, but strictly read-only
  // (no edit callbacks below), so they can see who checked in without the
  // owner-only write actions.
  const showCheckin = showsCheckinSection || admin;
  const canEditGuest = showsCheckinSection;
  const canEditTimes = showsCheckinSection;

  const guestFullName = booking
    ? `${booking.guest_first_name || ""} ${booking.guest_last_name || ""}`.trim() ||
      (t("bookingDetails.unknownGuest") as string)
    : "";

  let sectionCount = 0;
  const nextSectionNum = () => String(++sectionCount).padStart(2, "0");

  const bookingSectionNum = booking ? nextSectionNum() : "01";
  const guestSectionNum = booking ? nextSectionNum() : "02";
  const checkinSectionNum = showCheckin ? nextSectionNum() : "";
  const accessCodesSectionNum = smartLocks.length > 0 ? nextSectionNum() : "";
  const cleaningSectionNum = booking?.cleaning_task ? nextSectionNum() : "";
  const staySummarySectionNum = showStaySummary ? nextSectionNum() : "";

  const handleCloseDeleteModal = () => setAccessCodeToDelete(null);

  const totalExpectedGuests = booking
    ? (booking.adults_count ?? 0) + (booking.children_count ?? 0) + (booking.infants_count ?? 0)
    : 0;

  const translateGuestError = (err: unknown): string => {
    const code = err instanceof ApiClientError ? err.code : null;
    const key = code ? `guestDetailsDrawer.error.${code}` : null;
    if (key && t(key) !== key) return t(key) as string;
    return t("guestDetailsDrawer.saveError");
  };

  const handleSaveGuest = async (data: GuestRegistrationRequest) => {
    if (!bookingId) return;
    setGuestSaveError(null);
    setGuestSaving(true);
    try {
      if (selectedGuestId) {
        await apiClient.updateGuest(selectedGuestId, data);
        showSuccessToast(t("guestDetailsDrawer.toast.updated"));
      } else {
        await apiClient.createBookingGuest(bookingId, data);
        showSuccessToast(t("guestDetailsDrawer.toast.created"));
      }
      await refetch();
      setSelectedGuestId(null);
      setPendingSlotIndex(null);
      setGuestDrawerMode("view");
      onUpdate?.();
    } catch (err) {
      setGuestSaveError(translateGuestError(err));
    } finally {
      setGuestSaving(false);
    }
  };

  const handleDeleteGuest = async () => {
    if (!selectedGuestId) return;
    try {
      await apiClient.deleteGuest(selectedGuestId);
      showSuccessToast(t("guestDetailsDrawer.toast.deleted"));
      setSelectedGuestId(null);
      setGuestDrawerMode("view");
      await refetch();
      onUpdate?.();
    } catch (err) {
      // Keep the confirmation modal open so the user can retry / cancel.
      showErrorToast(translateGuestError(err));
      throw err;
    }
  };

  return (
    <>
      <DetailsDrawer
        opened={opened}
        onClose={onClose}
        loading={loading}
        header={
          booking && (
            <BookingPanelHeader
              booking={booking}
              checkInShort={dateInfo.checkInShort}
              checkOutShort={dateInfo.checkOutShort}
              totalNights={dateInfo.totalNights}
              guestFullName={guestFullName}
              canEditTimes={canEditTimes}
              editingTimes={editingTimes}
              timesLoading={actionLoading}
              onStartEditingTimes={() => setEditingTimes(true)}
              onSaveTimes={handleSaveStayTimes}
              onCancelEditingTimes={() => setEditingTimes(false)}
              onClose={onClose}
            />
          )
        }
      >
        {error && (
          <Alert color="red" mt="md" title={t("bookingDetails.error") as string}>
            {error}
          </Alert>
        )}

        {booking && (
          <>
            <BookingInfoSection
              sectionNum={bookingSectionNum}
              bookedAt={dateInfo.createdAtLong}
              channel={booking.channel}
              channelId={booking.channel_id}
              totalNights={dateInfo.totalNights}
              sesCommunications={booking.ses_communications}
            />

            <GuestDetailsForm
              sectionNum={guestSectionNum}
              initialData={guestInitialData}
              onSave={handleGuestDetailsSave}
              loading={actionLoading}
              canEdit={canEditGuest}
              showFirstName={canEditGuest || booking.guest_first_name != null}
              showLastName={canEditGuest || booking.guest_last_name != null}
              contactRequired={(booking.channel ?? "").toLowerCase() === "direct"}
            />

            {showCheckin && (
              <CheckinSection
                sectionNum={checkinSectionNum}
                booking={booking}
                // Clicking a registered guest opens the drawer in view mode. In
                // admin mode this is read-only (canEdit/canDelete stay false
                // below), but the cards remain clickable to inspect details.
                onGuestClick={(guestId) => {
                  setPendingSlotIndex(null);
                  setGuestSaveError(null);
                  setGuestDrawerMode("view");
                  setSelectedGuestId(guestId);
                }}
                // Admin mode stays read-only: omitting this callback keeps the
                // pending-slot placeholders non-interactive (no guest creation).
                onPendingSlotClick={
                  admin
                    ? undefined
                    : (slotIndex) => {
                        setSelectedGuestId(null);
                        setGuestSaveError(null);
                        setGuestDrawerMode("create");
                        setPendingSlotIndex(slotIndex);
                      }
                }
              />
            )}

            {smartLocks.length > 0 && (
              <AccessCodesSection
                sectionNum={accessCodesSectionNum}
                accessCodes={booking.access_codes || []}
                onCreateAccessCode={handleCreateAccessCode}
                onDeleteAccessCode={setAccessCodeToDelete}
                loading={actionLoading}
                isCompleted={new Date(booking.to) < new Date()}
              />
            )}

            {booking.cleaning_task && (
              <CleaningTaskSection
                sectionNum={cleaningSectionNum}
                task={booking.cleaning_task}
                team={team}
                defaultCleanerId={defaultCleanerId}
                onTaskUpdated={(task) => setBooking((prev) => (prev ? { ...prev, cleaning_task: task } : prev))}
                onChange={onUpdate}
              />
            )}

            {showStaySummary && (
              <GuestStaySummarySection
                sectionNum={staySummarySectionNum}
                initialData={staySummaryInitialData}
                checkoutReport={booking.cleaning_task?.job_report ?? null}
                onSave={handleSaveStaySummary}
                loading={actionLoading}
              />
            )}
          </>
        )}
      </DetailsDrawer>

      <DeleteAccessCodeModal
        opened={accessCodeToDelete !== null}
        onClose={handleCloseDeleteModal}
        onConfirm={handleDeleteAccessCode}
        loading={actionLoading}
      />

      <GuestDetailsDrawer
        guest={selectedGuest}
        opened={guestDrawerOpened}
        mode={guestDrawerMode}
        onClose={() => {
          setSelectedGuestId(null);
          setPendingSlotIndex(null);
          setGuestDrawerMode("view");
          setGuestSaveError(null);
        }}
        bookingCode={booking?.channel_id ?? null}
        sesSubmittedAt={booking ? sesLockSubmittedAt(booking) : null}
        canEdit={canEditGuest}
        canDelete={canEditGuest && selectedGuestId !== null}
        onDelete={handleDeleteGuest}
        onSave={handleSaveGuest}
        saving={guestSaving}
        submitError={guestSaveError}
        slotIndex={pendingSlotIndex}
        totalSlots={totalExpectedGuests}
        bookingHasMinors={
          (booking?.children_count ?? 0) + (booking?.infants_count ?? 0) > 0
        }
      />
    </>
  );
}
