import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Select, TextInput } from "@mantine/core";
import { DatePickerInput } from "@mantine/dates";
import { IconSearch } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";
import { AdminPropertySearchSelect } from "~/components/admin/admin-property-search-select";
import type { AdminProperty } from "~/types/admin-lists";

// Channel values match the backend BookingChannel enum names
const CHANNELS = [
  { value: "Airbnb", label: "Airbnb" },
  { value: "Booking", label: "Booking.com" },
  { value: "Vrbo", label: "VRBO" },
  { value: "Google", label: "Google" },
  { value: "Direct", label: "Direct" },
];

const today = dayjs();

interface AdminBookingsFilterPanelProps {
  /**
   * Initial options for the property dropdown (first page plus the selected
   * property); typing in the dropdown searches the backend instead.
   */
  properties: AdminProperty[];
  inDrawer?: boolean;
  /**
   * Effective date range the loader applied when the URL has no `from`/`to`. Used
   * only to display the active default window; selecting a range writes the URL.
   */
  defaultFrom?: string | null;
  defaultTo?: string | null;
}

/**
 * Admin bookings filter panel: debounced search + property (server-searched) + stay
 * date range + channel. All filters are server-side via URL params and reset `page`.
 */
export function AdminBookingsFilterPanel({
  properties,
  inDrawer = false,
  defaultFrom = null,
  defaultTo = null,
}: AdminBookingsFilterPanelProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  // Mirrors the URL params plus any navigation we have issued that React Router has
  // not committed yet. Every param write reads from and writes back to this ref, so
  // a debounced field firing while another field's navigation is still in flight
  // builds on that pending write instead of a stale snapshot that would drop it.
  const pendingParamsRef = useRef(searchParams);
  useEffect(() => {
    pendingParamsRef.current = searchParams;
  }, [searchParams]);
  const { t } = useTranslation();

  const queryFromUrl = searchParams.get("query") ?? "";
  const [localSearch, setLocalSearch] = useState(queryFromUrl);

  useEffect(() => {
    setLocalSearch(queryFromUrl);
  }, [queryFromUrl]);

  // Fall back to the loader's default range so the active default window is visible
  // when the URL has no explicit `from`/`to`. `dates=all` means the user explicitly
  // removed the date filter, so show an empty input instead of the default window.
  const allDates = searchParams.get("dates") === "all";
  const from = allDates ? null : (searchParams.get("from") ?? defaultFrom);
  const to = allDates ? null : (searchParams.get("to") ?? defaultTo);
  const dateRange: [string | null, string | null] = [from, to];
  const selectedChannel = searchParams.get("channel") ?? "all";
  const selectedProperty = searchParams.get("property_id");

  const channelData = [
    { value: "all", label: t("filters.allChannels") },
    ...CHANNELS,
  ];

  const datePresets = [
    {
      label: t("filters.datePreset.last90days"),
      value: [
        today.subtract(90, "day").format("YYYY-MM-DD"),
        today.format("YYYY-MM-DD"),
      ] as [string, string],
    },
    {
      label: t("filters.datePreset.last30days"),
      value: [
        today.subtract(30, "day").format("YYYY-MM-DD"),
        today.format("YYYY-MM-DD"),
      ] as [string, string],
    },
    {
      label: t("filters.datePreset.today"),
      value: [today.format("YYYY-MM-DD"), today.format("YYYY-MM-DD")] as [
        string,
        string,
      ],
    },
    {
      label: t("filters.datePreset.next7days"),
      value: [
        today.format("YYYY-MM-DD"),
        today.add(7, "day").format("YYYY-MM-DD"),
      ] as [string, string],
    },
    {
      label: t("filters.datePreset.next30days"),
      value: [
        today.format("YYYY-MM-DD"),
        today.add(30, "day").format("YYYY-MM-DD"),
      ] as [string, string],
    },
    {
      label: t("filters.datePreset.next90days"),
      value: [
        today.format("YYYY-MM-DD"),
        today.add(90, "day").format("YYYY-MM-DD"),
      ] as [string, string],
    },
    {
      label: t("filters.datePreset.nextYear"),
      value: [
        today.format("YYYY-MM-DD"),
        today.add(1, "year").format("YYYY-MM-DD"),
      ] as [string, string],
    },
  ];

  // Applies params (null deletes) to the URL, resetting page.
  const writeParams = (updates: Record<string, string | null>) => {
    const params = new URLSearchParams(pendingParamsRef.current);
    for (const [key, value] of Object.entries(updates)) {
      if (value) {
        params.set(key, value);
      } else {
        params.delete(key);
      }
    }
    params.delete("page");
    pendingParamsRef.current = params;
    navigate({ search: params.toString() ? `?${params.toString()}` : "" });
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      if (localSearch === (pendingParamsRef.current.get("query") ?? "")) return;
      writeParams({ query: localSearch || null });
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch]);

  const updateParam = (key: string, value: string | null) => {
    writeParams({ [key]: value && value !== "all" ? value : null });
  };

  // Clearing the range (the input's clear button) sets `dates=all` so the loader
  // skips its default window and returns all bookings; picking any date drops it.
  const handleDateRangeChange = (value: [string | null, string | null]) => {
    const cleared = !value[0] && !value[1];
    writeParams({
      from: value[0],
      to: value[1],
      dates: cleared ? "all" : null,
    });
  };

  return (
    <div
      className={
        inDrawer
          ? "space-y-5"
          : "bg-slate-50 rounded-2xl border border-slate-200 p-4 space-y-5"
      }
    >
      <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
        {t("filters.title")}
      </p>

      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t("filters.period")}
        </p>
        <DatePickerInput
          type="range"
          clearable
          placeholder={
            allDates ? t("filters.allDates") : t("filters.periodPlaceholder")
          }
          value={dateRange}
          onChange={handleDateRangeChange}
          presets={datePresets}
          valueFormat="MMM D, YYYY"
          size="sm"
          radius="md"
          styles={{
            input: { backgroundColor: "white", fontSize: "12px", padding: "12px 12px" },
          }}
        />
      </div>

      <TextInput
        placeholder={t("filters.searchPlaceholder")}
        value={localSearch}
        onChange={(e) => setLocalSearch(e.currentTarget.value)}
        leftSection={<IconSearch size={14} />}
        leftSectionWidth={32}
        size="sm"
        radius="md"
        styles={{
          input: {
            backgroundColor: "white",
            fontSize: "12px",
            paddingInlineStart: "28px",
          },
        }}
      />

      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t("filters.property")}
        </p>
        <AdminPropertySearchSelect
          properties={properties}
          value={selectedProperty}
          onChange={(v) => updateParam("property_id", v)}
        />
      </div>

      <div className="space-y-2">
        <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
          {t("filters.channel")}
        </p>
        <Select
          value={selectedChannel}
          onChange={(v) => updateParam("channel", v)}
          data={channelData}
          size="sm"
          radius="md"
          allowDeselect={false}
          styles={{ input: { backgroundColor: "white", fontSize: "12px" } }}
        />
      </div>
    </div>
  );
}
