import { useEffect, useState } from "react";
import { Alert, Button, Group, Stack } from "@mantine/core";
import {
  IconBucketDroplet,
  IconCalendarTime,
  IconPlant,
  IconPlus,
  IconSparkles,
  IconTools,
  IconX,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import { MainButton } from "~/components/main-button/main-button";
import { DetailsDrawer } from "~/components/details-drawer/details-drawer";
import drawerClasses from "~/components/details-drawer/details-drawer.module.css";
import {
  emptyTaskFormValues,
  pickerToIso,
  TaskFormFields,
  type PropertyOption,
  type TaskFormValues,
} from "~/components/task-form-fields";
import type { TaskType } from "~/types/tasks";

interface TaskCreateDrawerProps {
  opened: boolean;
  onClose: () => void;
  properties: PropertyOption[];
  /** Property prefilled from the clicked calendar cell. */
  initialPropertyId: string | null;
  /** Day prefilled from the clicked calendar cell. */
  initialDate: Date | null;
  /** Called after a task is successfully created (e.g. to revalidate). */
  onCreated: () => void;
}

/** Mirror the detail drawers' header icon, keyed off the chosen task type. */
function typeIcon(taskType: string | null) {
  switch (taskType) {
    case "maintenance":
      return IconTools;
    case "pool":
      return IconBucketDroplet;
    case "gardening":
      return IconPlant;
    case "cleaning":
      return IconSparkles;
    default:
      return IconCalendarTime;
  }
}

/**
 * Quick task creation, opened from a calendar day cell. Built on the same
 * `DetailsDrawer` shell as the booking/task detail panels so create and detail
 * share identical chrome (right-side drawer, header, width). Only the body —
 * the shared `TaskFormFields` — differs, since creating is a distinct job from
 * viewing/editing an existing task.
 */
export function TaskCreateDrawer({
  opened,
  onClose,
  properties,
  initialPropertyId,
  initialDate,
  onCreated,
}: TaskCreateDrawerProps) {
  const { t } = useTranslation();
  const [values, setValues] = useState<TaskFormValues>(() =>
    emptyTaskFormValues(),
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reseed the form each time the drawer opens for a (possibly different) cell.
  useEffect(() => {
    if (!opened) return;
    setValues(
      emptyTaskFormValues(initialDate ?? undefined, initialPropertyId ?? null),
    );
    setError(null);
  }, [opened, initialDate, initialPropertyId]);

  const handleChange = (patch: Partial<TaskFormValues>) =>
    setValues((v) => ({ ...v, ...patch }));

  // Title tracks the property chosen in the form (like the detail drawers,
  // which title on the property name); prompts to pick one until then.
  const selectedProperty = properties.find((p) => p.id === values.propertyId);

  const handleSubmit = async () => {
    if (!values.propertyId || !values.taskType || !values.from || !values.to) {
      setError(t("tasksNew.validateRequired"));
      return;
    }
    if (!values.assignedTo) {
      setError(t("tasksNew.validateAssignee"));
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.createTask({
        property_id: values.propertyId,
        task_type: values.taskType as TaskType,
        assigned_to: values.assignedTo,
        from: pickerToIso(values.from),
        to: pickerToIso(values.to),
        notes: values.notes || undefined,
      });
      onCreated();
      onClose();
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `tasksNew.error.${code}` : null;
      setError(
        key && t(key) !== key
          ? t(key)
          : err instanceof ApiClientError
            ? err.message
            : t("tasksNew.errorTitle"),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const TypeIcon = typeIcon(values.taskType);

  const header = (
    <div className={drawerClasses.dhead}>
      <div className={drawerClasses.dheadTop}>
        <div className={drawerClasses.channelBadge}>
          <TypeIcon stroke={1.8} />
        </div>
        <div className={drawerClasses.dheadTitleWrap}>
          <div className={drawerClasses.dheadKicker}>
            <span>{t("tasksNew.breadcrumb")}</span>
          </div>
          <h2>
            {selectedProperty?.name ?? t("tasksNew.fieldPropertyPlaceholder")}
          </h2>
        </div>
        <div className={drawerClasses.dheadActions}>
          <button
            type="button"
            className={`${drawerClasses.iconBtn} ${drawerClasses.iconBtnClose}`}
            onClick={onClose}
            aria-label={t("common.close")}
          >
            <IconX />
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <DetailsDrawer
      opened={opened}
      onClose={onClose}
      loading={submitting}
      header={header}
    >
      <Stack gap="lg" pt="md">
        {error && (
          <Alert color="red" title={t("tasksNew.errorTitle")}>
            {error}
          </Alert>
        )}

        <TaskFormFields
          properties={properties}
          values={values}
          onChange={handleChange}
        />

        <Group justify="flex-end" mt="sm">
          <Button variant="light" onClick={onClose} disabled={submitting}>
            {t("tasksNew.cancel")}
          </Button>
          <MainButton
            onClick={handleSubmit}
            loading={submitting}
            leftSection={<IconPlus size={16} />}
          >
            {t("tasksNew.create")}
          </MainButton>
        </Group>
      </Stack>
    </DetailsDrawer>
  );
}
