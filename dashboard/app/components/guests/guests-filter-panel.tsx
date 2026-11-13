import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Select, TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { Property } from "~/types/properties";

const EMPTY_PROPERTIES: Property[] = [];

interface GuestsFilterPanelProps {
  properties?: Property[];
  inDrawer?: boolean;
}

export function GuestsFilterPanel({ properties = EMPTY_PROPERTIES, inDrawer = false }: GuestsFilterPanelProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const searchParamsRef = useRef(searchParams);
  searchParamsRef.current = searchParams;
  const { t } = useTranslation();

  const queryFromUrl = searchParams.get("query") ?? "";
  const [localSearch, setLocalSearch] = useState(queryFromUrl);

  useEffect(() => {
    setLocalSearch(queryFromUrl);
  }, [queryFromUrl]);

  const selectedProperty = searchParams.get("property_id") ?? "all";

  const propertyData = [
    { value: "all", label: t("filters.allProperties") },
    ...properties.map((p) => ({ value: p.id, label: p.name })),
  ];

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

  return (
    <div className={inDrawer ? "space-y-5" : "bg-slate-50 rounded-2xl border border-slate-200 p-4 space-y-5"}>
      <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
        {t("filters.title")}
      </p>

      <TextInput
        placeholder={t("guests.searchPlaceholder")}
        value={localSearch}
        onChange={(e) => setLocalSearch(e.currentTarget.value)}
        leftSection={<IconSearch size={14} />}
        leftSectionWidth={32}
        size="sm"
        radius="md"
        styles={{ input: { backgroundColor: "white", fontSize: "12px", paddingInlineStart: "28px" } }}
      />

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
    </div>
  );
}
