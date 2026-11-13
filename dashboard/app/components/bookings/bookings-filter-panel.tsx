import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Select, TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { DatePickerInput } from "@mantine/dates";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";
import type { Property } from "~/types/properties";

// Channel values match the backend BookingChannel enum names
const CHANNELS = [
  { value: "Airbnb", label: "Airbnb" },
  { value: "Booking", label: "Booking.com" },
  { value: "Vrbo", label: "VRBO" },
  { value: "Google", label: "Google" },
  { value: "Direct", label: "Direct" },
];

const today = dayjs();

const EMPTY_PROPERTIES: Property[] = [];

interface BookingsFilterPanelProps {
  onClose?: () => void;
  properties?: Property[];
  inDrawer?: boolean;
}

export function BookingsFilterPanel({ onClose: _onClose, properties = EMPTY_PROPERTIES, inDrawer = false }: BookingsFilterPanelProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const searchParamsRef = useRef(searchParams);
  searchParamsRef.current = searchParams;
  const { t } = useTranslation();

  // Local state for the search input (debounced before writing to URL)
  const queryFromUrl = searchParams.get("query") ?? "";
  const [localSearch, setLocalSearch] = useState(queryFromUrl);

  // Keep localSearch in sync when the URL query param changes from another instance
  // (e.g. user typed in the drawer, then resized to see the sidebar)
  useEffect(() => {
    setLocalSearch(queryFromUrl);
  }, [queryFromUrl]);

  // Derived state from URL params for all other filters
  const from = searchParams.get("from");
  const to = searchParams.get("to");
  const dateRange: [string | null, string | null] = [from, to];
  const selectedProperty = searchParams.get("property_id") ?? "all";
  const selectedChannel = searchParams.get("channel") ?? "all";

  const propertyData = [
    { value: "all", label: t("filters.allProperties") },
    ...properties.map((p) => ({ value: p.id, label: p.name })),
  ];

  const channelData = [
    { value: "all", label: t("filters.allChannels") },
    ...CHANNELS,
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

  // Debounce search → URL
  useEffect(() => {
    const timer = setTimeout(() => {
      const params = new URLSearchParams(searchParamsRef.current);
      // Only navigate when the debounced value actually changes the URL —
      // otherwise mounting (page load, drawer open) would delete `page` and
      // bounce the user back to page 1.
      if (localSearch === (params.get("query") ?? "")) return;
      if (localSearch) {
        params.set("query", localSearch);
      } else {
        params.delete("query");
      }
      params.delete("page");
      navigate({ search: params.toString() ? `?${params.toString()}` : "" });
    }, 300);
    return () => clearTimeout(timer);
  }, [localSearch]);

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
    if (value[0]) params.set("from", value[0]); else params.delete("from");
    if (value[1]) params.set("to", value[1]); else params.delete("to");
    params.delete("page");
    navigate({ search: params.toString() ? `?${params.toString()}` : "" });
  };

  return (
    <div className={inDrawer ? "space-y-5" : "bg-slate-50 rounded-2xl border border-slate-200 p-4 space-y-5"}>
      <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
        {t("filters.title")}
      </p>

      {/* Search */}
      <TextInput
        placeholder={t("filters.searchPlaceholder")}
        value={localSearch}
        onChange={(e) => setLocalSearch(e.currentTarget.value)}
        leftSection={<IconSearch size={14} />}
        leftSectionWidth={32}
        size="sm"
        radius="md"
        styles={{ input: { backgroundColor: "white", fontSize: "12px", paddingInlineStart: "28px" } }}
      />

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

      {/* Channel */}
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
