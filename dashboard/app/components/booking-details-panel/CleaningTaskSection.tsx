import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import {
  ApiClient,
  ApiClientError,
  createApiConfig,
  handleApiError,
} from "~/lib/api-client";
import { CleanerSelect } from "~/components/cleaner-select";
import { EditableTextarea } from "~/components/editable-textarea";
import { ScheduleEditor } from "~/components/schedule-editor/schedule-editor";
import { UnsavedChangesBar } from "~/components/unsaved-changes-bar/unsaved-changes-bar";
import type { PropertyMember } from "~/types/properties";
import type { Task, UpdateTaskRequest } from "~/types/tasks";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";

const classes = { ...shared, ...local };

interface CleaningTaskSectionProps {
  sectionNum: string;
  task: Task;
  team: PropertyMember[];
  defaultCleanerId: string | null;
  onTaskUpdated: (task: Task) => void;
  onChange?: () => void;
}

export function CleaningTaskSection({
  sectionNum,
  task,
  team,
  defaultCleanerId,
  onTaskUpdated,
  onChange,
}: CleaningTaskSectionProps) {
  const { t } = useTranslation();
  const apiClient = useMemo(
    () => ApiClient.createFromBrowser(createApiConfig()),
    [],
  );

  // Pre-fill the cleaner picker with the property default when the task has no
  // assignee yet, but treat that pre-fill as the baseline so the form isn't
  // dirty on open. User has to actually change the selection to trigger a save.
  const baselineAssignedTo = task.assigned_to || defaultCleanerId || null;

  const [assignedTo, setAssignedTo] = useState<string | null>(baselineAssignedTo);
  const [notes, setNotes] = useState(task.notes ?? "");
  const [from, setFrom] = useState<string | null>(task.from ?? null);
  const [to, setTo] = useState<string | null>(task.to ?? null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setAssignedTo(task.assigned_to || defaultCleanerId || null);
    setNotes(task.notes ?? "");
    setFrom(task.from ?? null);
    setTo(task.to ?? null);
  }, [task.id, task.assigned_to, task.notes, task.from, task.to, defaultCleanerId]);

  const cleanerDirty = assignedTo !== baselineAssignedTo;
  const notesDirty = notes !== (task.notes ?? "");
  const fromDirty = from !== task.from;
  const toDirty = to !== task.to;
  const scheduleDirty = fromDirty || toDirty;
  const dirty = cleanerDirty || notesDirty || scheduleDirty;

  const isCompleted = task.status === "completed";
  const scheduleValid =
    !scheduleDirty || (!!from && !!to && new Date(from) < new Date(to));
  const canSave = dirty && scheduleValid && !saving;

  const handleCancel = () => {
    setAssignedTo(baselineAssignedTo);
    setNotes(task.notes ?? "");
    setFrom(task.from ?? null);
    setTo(task.to ?? null);
  };

  const handleSave = async () => {
    if (!scheduleValid) return;
    const payload: UpdateTaskRequest = {};
    if (cleanerDirty && assignedTo) payload.assigned_to = assignedTo;
    if (notesDirty) payload.notes = notes;
    if (fromDirty && from) payload.from = from;
    if (toDirty && to) payload.to = to;
    setSaving(true);
    try {
      await apiClient.updateTask(task.id, payload);
      const selectedMember =
        cleanerDirty && assignedTo
          ? team.find((m) => m.id === assignedTo)
          : null;
      onTaskUpdated({
        ...task,
        ...(cleanerDirty && assignedTo
          ? {
              assigned_to: assignedTo,
              assigned_to_name: selectedMember
                ? `${selectedMember.name} ${selectedMember.last_name}`
                : task.assigned_to_name,
            }
          : {}),
        ...(notesDirty ? { notes } : {}),
        ...(fromDirty && from ? { from } : {}),
        ...(toDirty && to ? { to } : {}),
      });
      showSuccessToast(t("bookingDetails.toast.cleaningTaskUpdated") as string);
      onChange?.();
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `bookingDetails.error.${code}` : null;
      const translated =
        key && t(key) !== key ? t(key) : handleApiError(err).message;
      showErrorToast(translated);
    } finally {
      setSaving(false);
    }
  };

  const scheduleErrorText =
    scheduleDirty && !scheduleValid
      ? (t("bookingDetails.error.task.invalid_schedule") as string)
      : undefined;

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("bookingDetails.cleaningTask") as string}
        </div>
        <span className={classes.pill}>
          {t("bookingDetails.scheduled") as string}
        </span>
      </div>

      <ScheduleEditor
        from={from}
        to={to}
        onFromChange={setFrom}
        onToChange={setTo}
        disabled={isCompleted}
        startLabel={t("bookingDetails.cleaningTaskStart") as string}
        endLabel={t("bookingDetails.cleaningTaskEnd") as string}
        errorText={scheduleErrorText}
      />

      <div style={{ marginTop: 14 }}>
        <CleanerSelect
          team={team}
          value={assignedTo}
          onChange={setAssignedTo}
          label={t("bookingDetails.assignedTo") as string}
          disabled={isCompleted}
          assignedToName={task.assigned_to_name}
        />
      </div>
      <EditableTextarea
        label={t("bookingDetails.notes") as string}
        value={notes}
        edit
        placeholder={t("bookingDetails.addNotesPlaceholder") as string}
        onChange={(e) => setNotes(e.currentTarget.value)}
        style={{ marginTop: 14 }}
      />
      {dirty && (
        <UnsavedChangesBar
          onCancel={handleCancel}
          onSave={handleSave}
          loading={saving}
          disabled={!canSave}
        />
      )}
    </div>
  );
}
