import { useEffect, useState } from "react";
import { useFetcher } from "react-router";
import { useTranslation } from "react-i18next";
import { Group, Stack, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import { MainButton } from "~/components/main-button/main-button";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { DetailsDrawer } from "~/components/details-drawer/details-drawer";
import { BookingPanelHeader } from "~/components/booking-details-panel/BookingPanelHeader";
import type { AdminSesActionResult } from "~/lib/admin-ses-action";
import {
  communicationStatus,
  type AdminSesBookingDetailResponse,
  type SesCommunicationDto,
  type SesCommunicationStatus,
  type SesCommunicationType,
  type SesCommunicationUiStatus,
  type SesHospedajesSubmission,
} from "~/types/ses-monitoring";

type CommBadgeStyle = { dot: string; labelKey: string; strike?: boolean };

const COMM_STATUS_STYLES: Record<
  SesCommunicationUiStatus | "none",
  CommBadgeStyle
> = {
  submitted: { dot: "bg-cyan-500", labelKey: "adminSes.status.submitted" },
  processing: { dot: "bg-cyan-500", labelKey: "adminSes.status.processing" },
  success: { dot: "bg-green-500", labelKey: "adminSes.status.success" },
  rejected: { dot: "bg-red-500", labelKey: "adminSes.status.rejected" },
  error: { dot: "bg-red-500", labelKey: "adminSes.status.error" },
  cancelling: { dot: "bg-amber-500", labelKey: "adminSes.status.cancelling" },
  cancelled: {
    dot: "bg-slate-400",
    labelKey: "adminSes.status.cancelled",
    strike: true,
  },
  none: { dot: "bg-slate-300", labelKey: "adminSes.status.none" },
};

function CommBadge({
  comms,
  type,
}: {
  comms: SesCommunicationDto[] | null | undefined;
  type: SesCommunicationType;
}) {
  const { t } = useTranslation();
  const status = communicationStatus(comms, type) ?? "none";
  const style = COMM_STATUS_STYLES[status];
  return (
    <div className="inline-flex h-7 items-center gap-2 px-2 rounded-lg border border-slate-200 bg-white">
      <span className="text-[10px] font-bold uppercase text-slate-400">
        {type}
      </span>
      <span className={`w-2 h-2 rounded-full ${style.dot}`} />
      <span
        className={`text-xs leading-none ${
          style.strike ? "line-through text-slate-400" : "text-slate-700"
        }`}
      >
        {t(style.labelKey)}
      </span>
    </div>
  );
}

function formatShortDate(dateStr: string): string {
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

/**
 * Admin drawer showing a booking's SES Hospedajes state: PV/RH communication badges,
 * submit/anular/refresh actions, and the full submission audit log. Loads its own detail
 * payload when `bookingId` becomes non-null, and posts actions to the current route —
 * the hosting route must delegate its `action` to `handleAdminSesAction`.
 */
export function SesBookingDrawer({
  bookingId,
  onClose,
}: {
  bookingId: string | null;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const fetcher = useFetcher<AdminSesActionResult>();
  const [drawerData, setDrawerData] =
    useState<AdminSesBookingDetailResponse | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

  // Load the detail payload whenever a booking is selected.
  useEffect(() => {
    if (!bookingId) {
      setDrawerData(null);
      return;
    }
    let stale = false;
    setDrawerData(null);
    setDrawerLoading(true);
    const apiClient = ApiClient.createFromBrowser(createApiConfig());
    apiClient
      .getAdminSesBookingDetail(bookingId)
      .then((response) => {
        if (!stale) setDrawerData(response.data);
      })
      .catch((e) => {
        if (stale) return;
        const code = e instanceof ApiClientError ? e.code : null;
        showErrorToast(code ?? "Failed to load submission history.");
      })
      .finally(() => {
        if (!stale) setDrawerLoading(false);
      });
    return () => {
      stale = true;
    };
  }, [bookingId]);

  // Surface action outcomes via toasts. Track the last action id we toasted for so a
  // remount (or revisit) doesn't replay a stale fetcher.data — fetcher state persists
  // across navigations and would otherwise show a stale toast for an action the admin
  // completed minutes ago on a previous page view.
  const [lastToastedActionId, setLastToastedActionId] = useState<string | null>(
    null,
  );
  useEffect(() => {
    if (!fetcher.data || fetcher.state !== "idle") return;
    const d = fetcher.data;
    const actionId = `${d.bookingId ?? ""}:${d.intent ?? ""}:${d.ok}:${d.rejected}:${d.code ?? ""}:${d.error ?? ""}`;
    if (actionId === lastToastedActionId) return;
    setLastToastedActionId(actionId);
    if (d.ok) {
      const successKey =
        d.intent === "cancel"
          ? "adminSes.toast.cancelled"
          : d.intent === "poll"
            ? "adminSes.toast.refreshed"
            : "adminSes.toast.submitted";
      showSuccessToast(t(successKey));
    } else if (d.rejected) {
      showErrorToast(
        `${t("adminSes.toast.rejected")}: ${d.data?.submissions?.[0]?.respuesta_descripcion ?? t("adminSes.toast.seeAuditLog")}`,
      );
    } else if (d.error) {
      const key = d.code ? `adminSes.error.${d.code}` : null;
      const translated = key && t(key) !== key ? t(key) : d.error;
      showErrorToast(translated);
    }
  }, [fetcher.data, fetcher.state, t, lastToastedActionId]);

  // Reload drawer payload when an action completes for the booking we have open
  useEffect(() => {
    if (
      fetcher.state === "idle" &&
      fetcher.data?.data &&
      bookingId &&
      fetcher.data.bookingId === bookingId
    ) {
      setDrawerData(fetcher.data.data);
    }
  }, [fetcher.state, fetcher.data, bookingId]);

  const busy =
    fetcher.state !== "idle" &&
    fetcher.formData?.get("booking_id") === bookingId;

  const runAction = (
    intent: "submit" | "submit-rh" | "cancel" | "poll",
    communicationId?: string,
  ) => {
    if (!bookingId) return;
    fetcher.submit(
      {
        intent,
        booking_id: bookingId,
        ...(communicationId ? { communication_id: communicationId } : {}),
      },
      { method: "post" },
    );
  };

  return (
    <DetailsDrawer
      opened={bookingId !== null}
      onClose={onClose}
      loading={drawerLoading}
      header={
        drawerData && (
          <BookingPanelHeader
            booking={drawerData.booking}
            checkInShort={formatShortDate(drawerData.booking.from)}
            checkOutShort={formatShortDate(drawerData.booking.to)}
            totalNights={Math.ceil(
              (new Date(drawerData.booking.to).getTime() -
                new Date(drawerData.booking.from).getTime()) /
                (1000 * 60 * 60 * 24),
            )}
            guestFullName={
              `${drawerData.booking.guest_first_name ?? ""} ${drawerData.booking.guest_last_name ?? ""}`.trim() ||
              "—"
            }
            canEditTimes={false}
            editingTimes={false}
            timesLoading={false}
            onStartEditingTimes={() => {}}
            onSaveTimes={() => {}}
            onCancelEditingTimes={() => {}}
            onClose={onClose}
          />
        )
      }
    >
      {drawerData &&
        (() => {
          const booking = drawerData.booking;
          const comms = booking.ses_communications ?? [];
          const pvStatus = communicationStatus(comms, "pv");
          const rhStatus = communicationStatus(comms, "rh");
          const canSubmit = (s: SesCommunicationUiStatus | null) =>
            s === null || s === "error" || s === "rejected";
          return (
            <Stack gap="md" pt="md">
              <Group gap="xs">
                <CommBadge comms={comms} type="pv" />
                <CommBadge comms={comms} type="rh" />
                <Text size="xs" c="dimmed">
                  {drawerData.submissions.length} attempt(s)
                </Text>
              </Group>

              <Group gap="xs">
                <MainButton
                  size="xs"
                  disabled={
                    !booking.checkin_completed_at || !canSubmit(pvStatus)
                  }
                  loading={busy}
                  onClick={() => runAction("submit")}
                >
                  {t("adminSes.action.submit")}
                </MainButton>
                <MainButton
                  size="xs"
                  variant="default"
                  disabled={!canSubmit(rhStatus)}
                  loading={busy}
                  onClick={() => runAction("submit-rh")}
                >
                  {t("adminSes.action.submitRh")}
                </MainButton>
              </Group>

              {comms.length > 0 && (
                <Stack gap="xs">
                  <Text size="xs" fw={700} c="dimmed" tt="uppercase">
                    {t("adminSes.comm.title")}
                  </Text>
                  {comms.map((c) => (
                    <CommunicationCard
                      key={`${c.type}-${c.operation}`}
                      comm={c}
                      comms={comms}
                      busy={busy}
                      onAnular={(commId) => runAction("cancel", commId)}
                      onRefresh={(commId) => runAction("poll", commId)}
                    />
                  ))}
                </Stack>
              )}

              {drawerData.submissions.length === 0 && (
                <Text size="sm" c="dimmed">
                  No submission attempts recorded yet.
                </Text>
              )}
              {groupSubmissions(drawerData.submissions, comms).map((group) => (
                <Stack key={group.key} gap="xs">
                  {group.label && (
                    <Text size="xs" fw={700} c="dimmed" tt="uppercase">
                      {group.label}
                    </Text>
                  )}
                  {group.submissions.map((s) => (
                    <SubmissionCard key={s.id} submission={s} />
                  ))}
                </Stack>
              ))}
            </Stack>
          );
        })()}
    </DetailsDrawer>
  );
}

type SubmissionGroup = {
  key: string;
  label: string | null;
  submissions: SesHospedajesSubmission[];
};

// Group the audit rows by their ses_communication_id so each communication (PV/RH alta or
// anulación) reads as one block. Rows that predate the projection (null id) fall into a trailing
// "unlinked" group.
function groupSubmissions(
  submissions: SesHospedajesSubmission[],
  comms: SesCommunicationDto[] | null | undefined,
): SubmissionGroup[] {
  // Label each group by matching the submit row's lote back to its comm row (type + operation).
  const byLote = new Map<string, SesCommunicationDto>();
  (comms ?? []).forEach((c) => {
    if (c.lote) byLote.set(c.lote, c);
  });

  const groups = new Map<string, SubmissionGroup>();
  const order: string[] = [];
  for (const s of submissions) {
    const key = s.ses_communication_id ?? "unlinked";
    if (!groups.has(key)) {
      const comm = s.ses_lote ? byLote.get(s.ses_lote) : undefined;
      const label =
        key === "unlinked"
          ? null
          : comm
            ? `${comm.type.toUpperCase()} · ${comm.operation}`
            : null;
      groups.set(key, { key, label, submissions: [] });
      order.push(key);
    }
    groups.get(key)!.submissions.push(s);
  }
  return order.map((k) => groups.get(k)!);
}

const ENTRY_STATUS_DOT: Record<SesCommunicationStatus, string> = {
  submitted: "bg-cyan-500",
  processing: "bg-cyan-500",
  accepted: "bg-green-500",
  rejected: "bg-red-500",
  failed: "bg-red-500",
};

const ENTRY_STATUS_LABEL_KEY: Record<SesCommunicationStatus, string> = {
  submitted: "adminSes.status.submitted",
  processing: "adminSes.status.processing",
  accepted: "adminSes.status.success",
  rejected: "adminSes.status.rejected",
  failed: "adminSes.status.error",
};

const ANULACION_BLOCKING_STATUSES: SesCommunicationStatus[] = [
  "submitted",
  "processing",
  "accepted",
  "rejected",
];

// One ses_communication row, with an Anular action on an accepted alta that has a captured código
// and no active anulación yet. The cancel endpoint resolves the código server-side; the button is
// only enabled once it exists so we don't fire a request SES would reject.
function CommunicationCard({
  comm,
  comms,
  busy,
  onAnular,
  onRefresh,
}: {
  comm: SesCommunicationDto;
  comms: SesCommunicationDto[];
  busy: boolean;
  onAnular: (communicationId: string) => void;
  onRefresh: (communicationId: string) => void;
}) {
  const { t } = useTranslation();

  const hasActiveAnulacion = comms.some(
    (c) =>
      c.type === comm.type &&
      c.operation === "anulacion" &&
      ANULACION_BLOCKING_STATUSES.includes(c.status),
  );
  const isAcceptedAlta = comm.operation === "alta" && comm.status === "accepted";
  const canAnular = isAcceptedAlta && !!comm.codigo_comunicacion && !hasActiveAnulacion;
  // Anything that reached SES (has a lote) can be re-polled for its latest status.
  const canRefresh = !!comm.lote;

  return (
    <div className="border rounded-lg p-3 border-slate-200 bg-white">
      <Group justify="space-between" mb={6} wrap="nowrap">
        <Group gap={8}>
          <span className="text-[10px] font-bold uppercase text-slate-400">
            {comm.type}
          </span>
          <Text size="xs" fw={600}>
            {t(`adminSes.operation.${comm.operation}`)}
          </Text>
          <span className="inline-flex items-center gap-1.5">
            <span className={`w-2 h-2 rounded-full ${ENTRY_STATUS_DOT[comm.status]}`} />
            <Text size="xs" c="dimmed">
              {t(ENTRY_STATUS_LABEL_KEY[comm.status])}
            </Text>
          </span>
        </Group>
        <Group gap={6} wrap="nowrap">
          {canRefresh && (
            <MainButton
              size="xs"
              variant="ghost"
              loading={busy}
              leftSection={<IconRefresh size={14} />}
              onClick={() => onRefresh(comm.id)}
            >
              {t("adminSes.action.refresh")}
            </MainButton>
          )}
          {canAnular && (
            <MainButton
              size="xs"
              variant="danger"
              loading={busy}
              onClick={() => onAnular(comm.id)}
            >
              {t("adminSes.action.anular")}
            </MainButton>
          )}
        </Group>
      </Group>
      {comm.codigo_comunicacion && (
        <Text size="xs">
          <strong>{t("adminSes.comm.codigo")}:</strong>{" "}
          <span className="font-mono">{comm.codigo_comunicacion}</span>
        </Text>
      )}
      {comm.lote && (
        <Text size="xs">
          <strong>{t("adminSes.comm.lote")}:</strong>{" "}
          <span className="font-mono">{comm.lote}</span>
        </Text>
      )}
      {comm.estado_desc && <Text size="xs">{comm.estado_desc}</Text>}
      {comm.last_error && (
        <Text size="xs" c="red">
          {comm.last_error}
        </Text>
      )}
      {isAcceptedAlta && !comm.codigo_comunicacion && (
        <Text size="xs" c="dimmed">
          {t("adminSes.comm.codigoPending")}
        </Text>
      )}
    </div>
  );
}

function SubmissionCard({ submission }: { submission: SesHospedajesSubmission }) {
  const isError =
    submission.error_text !== null ||
    (submission.respuesta_codigo !== null && submission.respuesta_codigo !== 0);

  return (
    <div
      className={`border rounded-lg p-3 ${isError ? "border-red-200 bg-red-50/40" : "border-slate-200 bg-slate-50/40"}`}
    >
      <Group justify="space-between" mb={4}>
        <Text size="xs" fw={600}>
          {submission.operation.toUpperCase()}
        </Text>
        <Text size="xs" c="dimmed">
          {new Date(submission.attempted_at).toLocaleString()}
        </Text>
      </Group>
      {submission.respuesta_codigo !== null && (
        <Text size="xs">
          <strong>Codigo:</strong> {submission.respuesta_codigo}
          {submission.respuesta_descripcion
            ? ` — ${submission.respuesta_descripcion}`
            : ""}
        </Text>
      )}
      {submission.ses_lote && (
        <Text size="xs">
          <strong>Lote:</strong> {submission.ses_lote}
        </Text>
      )}
      {submission.ses_estado && (
        <Text size="xs">
          <strong>Estado:</strong> {submission.ses_estado}
          {submission.ses_estado_desc
            ? ` — ${submission.ses_estado_desc}`
            : ""}
        </Text>
      )}
      {submission.error_text && (
        <Text size="xs" c="red">
          <strong>Error:</strong> {submission.error_text}
        </Text>
      )}
      {submission.response_xml && (
        <details className="mt-2">
          <summary className="text-xs cursor-pointer text-slate-500">
            Raw response XML
          </summary>
          <pre className="text-xs mt-1 p-2 bg-slate-100 rounded overflow-x-auto whitespace-pre-wrap">
            {submission.response_xml}
          </pre>
        </details>
      )}
    </div>
  );
}
