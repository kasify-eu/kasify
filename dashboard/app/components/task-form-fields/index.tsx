import { useEffect, useRef, useState } from "react";
import { Box, Select, SimpleGrid, Textarea } from "@mantine/core";
import { DateTimePicker } from "@mantine/dates";
import { useTranslation } from "react-i18next";
import { ApiClient, createApiConfig } from "~/lib/api-client";
import type { CrewMember } from "~/types/crew";

export type TaskFormValues = {
  propertyId: string | null;
  taskType: string | null;
  assignedTo: string | null;
  /** Mantine picker string, e.g. "2026-07-03 10:00:00". */
  from: string | null;
  to: string | null;
  notes: string;
};

export type PropertyOption = { id: string; name: string };

function pad(n: number): string {
  return n.toString().padStart(2, "0");
}

function pickerString(d: Date, hour: number, minute: number): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(hour)}:${pad(minute)}:00`;
}

/** Default from/to picker strings for a given day (defaults to today): 10:00 → 12:00. */
export function defaultTaskTimes(date: Date = new Date()): {
  from: string;
  to: string;
} {
  return { from: pickerString(date, 10, 0), to: pickerString(date, 12, 0) };
}

/** A fresh set of form values, optionally seeded with a day and property. */
export function emptyTaskFormValues(
  date: Date = new Date(),
  propertyId: string | null = null,
): TaskFormValues {
  const { from, to } = defaultTaskTimes(date);
  return {
    propertyId,
    taskType: "cleaning",
    assignedTo: null,
    from,
    to,
    notes: "",
  };
}

// "YYYY-MM-DD HH:mm:ss" (Mantine) → "YYYY-MM-DDTHH:mm:ss.000Z"
export function pickerToIso(value: string): string {
  const trimmed = value.length === 16 ? `${value}:00` : value;
  return `${trimmed.replace(" ", "T")}.000Z`;
}

interface TaskFormFieldsProps {
  properties: PropertyOption[];
  values: TaskFormValues;
  onChange: (patch: Partial<TaskFormValues>) => void;
}

/**
 * Controlled task-creation form body, shared by the standalone `/tasks/new`
 * page and the calendar's quick-create modal. The parent owns `values`; this
 * component owns the per-property crew fetch and default-cleaner preselection.
 *
 * It renders named inputs (plus hidden inputs for the date pickers) so a server
 * `<Form>` action can read the values by name; clients that submit
 * programmatically (the modal) simply read `values` and ignore the names.
 */
export function TaskFormFields({
  properties,
  values,
  onChange,
}: TaskFormFieldsProps) {
  const { t } = useTranslation();
  const [crew, setCrew] = useState<CrewMember[]>([]);
  const [defaultCleanerId, setDefaultCleanerId] = useState<string | null>(null);
  const [crewLoading, setCrewLoading] = useState(false);

  const { propertyId, taskType, assignedTo } = values;

  // Keep the latest onChange without making it an effect dependency.
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  // (Re)fetch crew whenever the property changes. The assignee is reset at the
  // source (the property Select's onChange), so this effect only fetches.
  useEffect(() => {
    setCrew([]);
    setDefaultCleanerId(null);
    if (!propertyId) return;

    let cancelled = false;
    setCrewLoading(true);
    const apiClient = ApiClient.createFromBrowser(createApiConfig());
    apiClient
      .getPropertyCrew(propertyId)
      .then((response) => {
        if (cancelled) return;
        setCrew(response.data.crew);
        setDefaultCleanerId(response.data.default_cleaner_id ?? null);
      })
      .catch(() => {
        if (!cancelled) setCrew([]);
      })
      .finally(() => {
        if (!cancelled) setCrewLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [propertyId]);

  // Pre-select the property's default cleaner for cleaning tasks, unless the
  // user has already picked someone. The field stays editable and required.
  useEffect(() => {
    if (assignedTo) return;
    if (taskType !== "cleaning") return;
    if (!defaultCleanerId) return;
    if (!crew.some((member) => member.id === defaultCleanerId)) return;
    onChangeRef.current({ assignedTo: defaultCleanerId });
  }, [assignedTo, taskType, defaultCleanerId, crew]);

  const propertyOptions = properties.map((p) => ({ value: p.id, label: p.name }));

  const crewOptions = crew.map((member) => {
    const fullName = `${member.name} ${member.last_name}`.trim();
    const serviceLabel = member.service
      ? t(`tasks.type.${member.service}`, { defaultValue: member.service })
      : null;
    return {
      value: member.id,
      label: serviceLabel ? `${fullName} · ${serviceLabel}` : fullName,
    };
  });

  const assigneePlaceholder = !propertyId
    ? t("tasksNew.fieldAssigneeSelectPropertyFirst")
    : crewLoading
      ? t("tasksNew.fieldAssigneeLoading")
      : t("tasksNew.fieldAssigneePlaceholder");

  return (
    <>
      <Select
        label={t("tasksNew.fieldProperty")}
        placeholder={t("tasksNew.fieldPropertyPlaceholder")}
        data={propertyOptions}
        value={propertyId}
        onChange={(value) => onChange({ propertyId: value, assignedTo: null })}
        required
        searchable
        name="propertyId"
      />

      <Select
        label={t("tasksNew.fieldType")}
        placeholder={t("tasksNew.fieldTypePlaceholder")}
        data={[
          { value: "cleaning", label: t("tasks.type.cleaning") },
          { value: "maintenance", label: t("tasks.type.maintenance") },
          { value: "pool", label: t("tasks.type.pool") },
          { value: "gardening", label: t("tasks.type.gardening") },
        ]}
        value={taskType}
        onChange={(value) => onChange({ taskType: value })}
        required
        name="taskType"
      />

      <Select
        label={t("tasksNew.fieldAssignee")}
        placeholder={assigneePlaceholder}
        data={crewOptions}
        value={assignedTo}
        onChange={(value) => onChange({ assignedTo: value })}
        required
        searchable
        name="assignedTo"
        disabled={!propertyId || crewLoading}
        nothingFoundMessage={t("tasksNew.fieldAssigneeEmpty")}
      />

      <SimpleGrid cols={2} spacing="md">
        <Box>
          <DateTimePicker
            label={t("tasksNew.fieldStartTime")}
            value={values.from}
            onChange={(value) => onChange({ from: value })}
            valueFormat="MMM D, YYYY HH:mm"
            required
          />
          <input type="hidden" name="from" value={values.from ?? ""} />
        </Box>
        <Box>
          <DateTimePicker
            label={t("tasksNew.fieldEndTime")}
            value={values.to}
            onChange={(value) => onChange({ to: value })}
            valueFormat="MMM D, YYYY HH:mm"
            required
          />
          <input type="hidden" name="to" value={values.to ?? ""} />
        </Box>
      </SimpleGrid>

      <Textarea
        label={t("tasksNew.fieldNotes")}
        placeholder={t("tasksNew.fieldNotesPlaceholder")}
        name="notes"
        value={values.notes}
        onChange={(e) => onChange({ notes: e.currentTarget.value })}
        minRows={3}
      />
    </>
  );
}
