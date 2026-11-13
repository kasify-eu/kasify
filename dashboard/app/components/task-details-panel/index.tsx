import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Modal,
  Stack,
  Text,
} from "@mantine/core";
import { IconTrash } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import {
  ApiClient,
  ApiClientError,
  createApiConfig,
  handleApiError,
} from "~/lib/api-client";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { DetailsDrawer } from "~/components/details-drawer/details-drawer";
import type { Task } from "~/types/tasks";
import type { PropertyMember } from "~/types/properties";
import { PropertyAccessLevel } from "~/types/properties";
import { CheckoutReportSection } from "./CheckoutReportSection";
import { CrewAssignmentSection } from "./CrewAssignmentSection";
import { NextBookingSection } from "./NextBookingSection";
import { PublicLinkSection } from "./PublicLinkSection";
import { TaskPanelHeader } from "./TaskPanelHeader";

interface TaskDetailsPanelProps {
  taskId: string | null;
  opened: boolean;
  onClose: () => void;
  onUpdate?: () => void;
}

export function TaskDetailsPanel({
  taskId,
  opened,
  onClose,
  onUpdate,
}: TaskDetailsPanelProps) {
  const [task, setTask] = useState<Task | null>(null);
  const [ownerNotes, setOwnerNotes] = useState("");
  const [checkoutNotes, setCheckoutNotes] = useState("");
  const [assignedTo, setAssignedTo] = useState<string | null>(null);
  const [team, setTeam] = useState<PropertyMember[]>([]);
  const [propertyRole, setPropertyRole] = useState<PropertyAccessLevel | null | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleteConfirmOpened, setDeleteConfirmOpened] = useState(false);
  const [scheduleFrom, setScheduleFrom] = useState<string | null>(null);
  const [scheduleTo, setScheduleTo] = useState<string | null>(null);
  const [scheduleSaving, setScheduleSaving] = useState(false);

  const { t } = useTranslation();

  const apiClient = useMemo(
    () => ApiClient.createFromBrowser(createApiConfig()),
    [],
  );

  const fetchTask = useCallback(
    async (id: string) => {
      setLoading(true);
      setError(null);
      try {
        const response = await apiClient.get<Task>(`/tasks/${id}`);
        const fetchedTask = response.data;
        setTask(fetchedTask);
        setOwnerNotes(fetchedTask.notes || "");
        setCheckoutNotes(fetchedTask.job_report || "");
        setAssignedTo(fetchedTask.assigned_to ?? null);
        setScheduleFrom(fetchedTask.from);
        setScheduleTo(fetchedTask.to);

        const propertyResponse = await apiClient.getProperty(
          fetchedTask.property.id,
        );
        setTeam(propertyResponse.data.team ?? []);
        setPropertyRole(propertyResponse.data.role ?? null);
      } catch (err) {
        const apiError = handleApiError(err);
        setError(apiError.message);
      } finally {
        setLoading(false);
      }
    },
    [apiClient],
  );

  useEffect(() => {
    if (taskId && opened) {
      setTask(null);
      setOwnerNotes("");
      setCheckoutNotes("");
      setAssignedTo(null);
      setScheduleFrom(null);
      setScheduleTo(null);
      void fetchTask(taskId);
    }
    if (!opened) {
      setError(null);
    }
  }, [taskId, opened, fetchTask]);

  const handleSaveCheckoutNotes = useCallback(async () => {
    if (!taskId) return;
    await apiClient.patch(`/tasks/${taskId}`, { job_report: checkoutNotes });
    setTask((prev) => (prev ? { ...prev, job_report: checkoutNotes } : prev));
    showSuccessToast(t("cleaningDetails.toast.checkoutReportSaved"), "✓");
    onUpdate?.();
  }, [taskId, apiClient, checkoutNotes, t, onUpdate]);

  const handleSaveCrewAssignment = useCallback(async () => {
    if (!task || !taskId) return;
    const assignedDirty = assignedTo !== task.assigned_to;
    const notesDirty = ownerNotes !== (task.notes ?? "");
    const payload: { assigned_to?: string; notes?: string } = {};
    if (assignedDirty && assignedTo) payload.assigned_to = assignedTo;
    if (notesDirty) payload.notes = ownerNotes;
    if (Object.keys(payload).length === 0) return;
    await apiClient.patch(`/tasks/${taskId}`, payload);
    setTask((prev) =>
      prev
        ? {
            ...prev,
            ...(assignedDirty && assignedTo ? { assigned_to: assignedTo } : {}),
            ...(notesDirty ? { notes: ownerNotes } : {}),
          }
        : prev,
    );
    showSuccessToast(
      t(
        notesDirty
          ? "cleaningDetails.toast.notesSaved"
          : "cleaningDetails.toast.cleanerSaved",
      ),
      "✓",
    );
    onUpdate?.();
  }, [task, taskId, apiClient, assignedTo, ownerNotes, t, onUpdate]);

  const scheduleDirty =
    !!task && (scheduleFrom !== task.from || scheduleTo !== task.to);
  const scheduleValid =
    !scheduleDirty ||
    (!!scheduleFrom &&
      !!scheduleTo &&
      new Date(scheduleFrom) < new Date(scheduleTo));

  const handleCancelSchedule = useCallback(() => {
    if (!task) return;
    setScheduleFrom(task.from);
    setScheduleTo(task.to);
  }, [task]);

  const handleSaveSchedule = useCallback(async () => {
    if (!task || !taskId || !scheduleDirty || !scheduleValid) return;
    const payload: { from?: string; to?: string } = {};
    if (scheduleFrom !== task.from && scheduleFrom) payload.from = scheduleFrom;
    if (scheduleTo !== task.to && scheduleTo) payload.to = scheduleTo;
    setScheduleSaving(true);
    try {
      const response = await apiClient.updateTask(taskId, payload);
      setTask(response.data);
      setScheduleFrom(response.data.from);
      setScheduleTo(response.data.to);
      showSuccessToast(t("cleaningDetails.toast.scheduleSaved"), "✓");
      onUpdate?.();
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `bookingDetails.error.${code}` : null;
      const translated =
        key && t(key) !== key ? t(key) : handleApiError(err).message;
      showErrorToast(translated);
    } finally {
      setScheduleSaving(false);
    }
  }, [
    task,
    taskId,
    apiClient,
    scheduleDirty,
    scheduleValid,
    scheduleFrom,
    scheduleTo,
    t,
    onUpdate,
  ]);

  const handleDeleteTask = async () => {
    if (!taskId) return;
    setLoading(true);
    try {
      await apiClient.deleteTask(taskId);
      showSuccessToast(t("cleaningDetails.toast.taskDeleted"), "✓");
      setDeleteConfirmOpened(false);
      onClose();
      onUpdate?.();
    } catch (err) {
      const apiError = handleApiError(err);
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  const canEdit =
    propertyRole === null
      || propertyRole === PropertyAccessLevel.Manager
      || propertyRole === PropertyAccessLevel.Owner;

  const nextBooking = task?.next_booking ?? null;

  const guestSummary = nextBooking
    ? [
        nextBooking.adults_count ? t("cleaningDetails.adultsCount", { count: nextBooking.adults_count }) : null,
        nextBooking.children_count
          ? t("cleaningDetails.childrenCount", { count: nextBooking.children_count })
          : null,
        nextBooking.infants_count
          ? t("cleaningDetails.infantsCount", { count: nextBooking.infants_count })
          : null,
      ]
        .filter(Boolean)
        .join(", ")
    : "";

  const isRedacted = !!task && !task.task_type;

  let sectionCount = 0;
  const nextSectionNum = () => String(++sectionCount).padStart(2, "0");

  const crewSectionNum = task && !isRedacted ? nextSectionNum() : "";
  const checkoutSectionNum = task && !isRedacted ? nextSectionNum() : "";
  const nextBookingSectionNum = task && !isRedacted && nextBooking ? nextSectionNum() : "";
  const publicLinkSectionNum = task && !isRedacted && task?.public_token ? nextSectionNum() : "";

  return (
    <>
      <DetailsDrawer
        opened={opened}
        onClose={onClose}
        loading={loading}
        header={
          task && (
            <TaskPanelHeader
              task={task}
              from={scheduleFrom}
              to={scheduleTo}
              onFromChange={setScheduleFrom}
              onToChange={setScheduleTo}
              scheduleDirty={scheduleDirty}
              scheduleValid={scheduleValid}
              scheduleSaving={scheduleSaving}
              onSaveSchedule={handleSaveSchedule}
              onCancelSchedule={handleCancelSchedule}
              canEdit={canEdit}
              onClose={onClose}
              onDelete={() => setDeleteConfirmOpened(true)}
            />
          )
        }
      >
        {error && (
          <Alert color="red" mt="md" title={t("cleaningDetails.error")}>
            {error}
          </Alert>
        )}

        {task && isRedacted && (
          <Alert color="gray" mt="md" title={t("taskDetails.foreignTask.title")}>
            {t("taskDetails.foreignTask.body")}
          </Alert>
        )}

        {task && !isRedacted && (
          <>
            <CrewAssignmentSection
              sectionNum={crewSectionNum}
              team={team}
              assignedTo={assignedTo}
              originalAssignedTo={task.assigned_to ?? null}
              notes={ownerNotes}
              originalNotes={task.notes || ""}
              onAssignedToChange={setAssignedTo}
              onNotesChange={setOwnerNotes}
              onSave={handleSaveCrewAssignment}
              canEdit={canEdit}
              disabled={task.status === "completed"}
            />

            <CheckoutReportSection
              sectionNum={checkoutSectionNum}
              notes={checkoutNotes}
              originalNotes={task.job_report || ""}
              onChange={setCheckoutNotes}
              onSave={handleSaveCheckoutNotes}
              disabled={task.status === "completed"}
              taskType={task.task_type}
            />

            {nextBooking && (
              <NextBookingSection
                sectionNum={nextBookingSectionNum}
                guestName={nextBooking.guest_first_name || t("cleaningDetails.nextGuest")}
                guestSummary={guestSummary}
                dateRange={`${nextBooking.from} → ${nextBooking.to}`}
                arrivalTime={nextBooking.arrival_time}
                channel={nextBooking.channel}
                specialRequests={nextBooking.special_requests ?? null}
              />
            )}

            {task.public_token && (
              <PublicLinkSection
                sectionNum={publicLinkSectionNum}
                publicToken={task.public_token}
              />
            )}
          </>
        )}
      </DetailsDrawer>

      <Modal
        opened={deleteConfirmOpened}
        onClose={() => setDeleteConfirmOpened(false)}
        withCloseButton={false}
        centered
        size="md"
        radius={32}
        padding={0}
        styles={{
          content: {
            borderRadius: 32,
            overflow: "hidden",
          },
        }}
      >
        <Box className="p-8">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <IconTrash size={24} className="text-red-600" />
              </div>
              <Text size="xl" fw={700} className="tracking-tight text-slate-900">
                {t("cleaningDetails.deleteTaskConfirmTitle")}
              </Text>
            </div>
            <button
              onClick={() => setDeleteConfirmOpened(false)}
              className="p-2 hover:bg-slate-100 rounded-full transition-colors"
            >
              <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {t("cleaningDetails.deleteTaskConfirmBody")}
            </Text>

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={() => setDeleteConfirmOpened(false)}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("common.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={loading}
                onClick={handleDeleteTask}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("cleaningDetails.deleteTask")}
              </Button>
            </div>
          </Stack>
        </Box>
      </Modal>
    </>
  );
}
