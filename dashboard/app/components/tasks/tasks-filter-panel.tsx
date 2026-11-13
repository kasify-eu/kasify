import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Select } from "@mantine/core";
import { DatePickerInput } from "@mantine/dates";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";
import { ApiClient, createApiConfig } from "~/lib/api-client";
import type { Property } from "~/types/properties";
import type { CrewMember } from "~/types/crew";

const today = dayjs();

const EMPTY_PROPERTIES: Property[] = [];

// Crew is only needed to populate the "assigned" filter, so it's fetched
// lazily in the browser instead of in the route loader (which would add one
// request per property to every /tasks navigation). Cached at module level so
// the sidebar and drawer instances share a single fetch per session.
let crewCache: Promise<CrewMember[]> | null = null;

function fetchAllCrew(properties: Property[]): Promise<CrewMember[]> {
  if (!crewCache) {
    const apiClient = ApiClient.createFromBrowser(createApiConfig());
    crewCache = Promise.all(
      properties.map((p) =>
        apiClient.getPropertyCrew(p.id).then((r) => r.data.crew).catch(() => [] as CrewMember[]),
      ),
    ).then((results) =>
      Array.from(new Map(results.flat().map((c) => [c.id, c])).values()),
    );
    // Don't cache a failed attempt — allow a retry on the next mount.
    crewCache.catch(() => {
      crewCache = null;
    });
  }
  return crewCache;
}

interface TasksFilterPanelProps {
  properties?: Property[];
  inDrawer?: boolean;
}

export function TasksFilterPanel({ properties = EMPTY_PROPERTIES, inDrawer = false }: TasksFilterPanelProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [crew, setCrew] = useState<CrewMember[]>([]);

  useEffect(() => {
    if (properties.length === 0) return;
    let cancelled = false;
    fetchAllCrew(properties).then((result) => {
      if (!cancelled) setCrew(result);
    });
    return () => {
      cancelled = true;
    };
  }, [properties]);

  const from = searchParams.get("scheduled_from");
  const to = searchParams.get("scheduled_to");
  const dateRange: [string | null, string | null] = [from, to];
  const selectedProperty = searchParams.get("property_id") ?? "all";
  const selectedType = searchParams.get("task_type") ?? "all";
  const selectedAssigned = searchParams.get("assigned_to") ?? "all";

  const taskTypes = [
    { value: "all", label: t("filters.allTypes") },
    { value: "cleaning", label: t("tasks.type.cleaning") },
    { value: "maintenance", label: t("tasks.type.maintenance") },
    { value: "pool", label: t("tasks.type.pool") },
    { value: "gardening", label: t("tasks.type.gardening") },
  ];

  const propertyData = [
    { value: "all", label: t("filters.allProperties") },
    ...properties.map((p) => ({ value: p.id, label: p.name })),
  ];

  const assignedData = [
    { value: "all", label: t("filters.allStaff") },
    ...crew.map((c) => ({ value: c.id, label: c.name })),
  ];

  const datePresets = [
    {
      label: t("filters.datePreset.last6months"),
      value: [today.subtract(6, "month").format("YYYY-MM-DD"), today.format("YYYY-MM-DD")] as [string, string],
    },
    {
      label: t("filters.datePreset.next7days"),
      value: [today.format("YYYY-MM-DD"), today.add(7, "day").format("YYYY-MM-DD")] as [string, string],
    },
    {
      label: t("filters.datePreset.next30days"),
      value: [today.format("YYYY-MM-DD"), today.add(30, "day").format("YYYY-MM-DD")] as [string, string],
    },
    {
      label: t("filters.datePreset.next3months"),
      value: [today.format("YYYY-MM-DD"), today.add(3, "month").format("YYYY-MM-DD")] as [string, string],
    },
    {
      label: t("filters.datePreset.next12months"),
      value: [today.format("YYYY-MM-DD"), today.add(12, "month").format("YYYY-MM-DD")] as [string, string],
    },
  ];

  const updateParam = (key: string, value: string | null) => {
    const params = new URLSearchParams(searchParams);
    if (value && value !== "all") {
      params.set(key, value);
    } else {
      params.delete(key);
    }
    params.delete("page");
    navigate({ search: params.toString() ? `?${params.toString()}` : "" });
  };

  const handleDateRangeChange = (value: [string | null, string | null]) => {
    const params = new URLSearchParams(searchParams);
    if (value[0]) params.set("scheduled_from", value[0]); else params.delete("scheduled_from");
    if (value[1]) params.set("scheduled_to", value[1]); else params.delete("scheduled_to");
    params.delete("page");
    navigate({ search: params.toString() ? `?${params.toString()}` : "" });
  };

  return (
    <div className={inDrawer ? "space-y-5" : "bg-slate-50 rounded-2xl border border-slate-200 p-4 space-y-5"}>
      <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
        {t("filters.title")}
      </p>

      {/* Type */}
      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t("filters.type")}
        </p>
        <Select
          value={selectedType}
          onChange={(v) => updateParam("task_type", v)}
          data={taskTypes}
          size="sm"
          radius="md"
          allowDeselect={false}
          styles={{ input: { backgroundColor: "white", fontSize: "12px" } }}
        />
      </div>

      {/* Period */}
      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t("filters.period")}
        </p>
        <DatePickerInput
          type="range"
          placeholder={t("filters.periodPlaceholder")}
          value={dateRange}
          onChange={handleDateRangeChange}
          presets={datePresets}
          valueFormat="MMM D"
          size="sm"
          radius="md"
          styles={{ input: { backgroundColor: "white", fontSize: "12px", padding: "12px 12px" } }}
        />
      </div>

      {/* Property */}
      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t("filters.property")}
        </p>
        <Select
          value={selectedProperty}
          onChange={(v) => updateParam("property_id", v)}
          data={propertyData}
          size="sm"
          radius="md"
          allowDeselect={false}
          styles={{ input: { backgroundColor: "white", fontSize: "12px" } }}
        />
      </div>

      {/* Assigned */}
      {assignedData.length > 1 && (
        <div className="space-y-2">
          <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
            {t("filters.assigned")}
          </p>
          <Select
            value={selectedAssigned}
            onChange={(v) => updateParam("assigned_to", v)}
            data={assignedData}
            size="sm"
            radius="md"
            allowDeselect={false}
            styles={{ input: { backgroundColor: "white", fontSize: "12px" } }}
          />
        </div>
      )}
    </div>
  );
}
