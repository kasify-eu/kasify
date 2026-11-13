import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { Select, TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

// Values match the `ses`/`nuki`/`unifi` query params of GET /admin/properties
const INTEGRATION_FILTERS = [
  { param: "ses", label: "SES" },
  { param: "nuki", label: "Nuki" },
  { param: "unifi", label: "Unifi" },
];

const VALID_FILTER_VALUES = ["configured", "not_configured"];

interface AdminPropertiesFilterPanelProps {
  placeholder?: string;
  inDrawer?: boolean;
}

/**
 * Admin properties filter panel: debounced free-text search plus a tri-state
 * (any / configured / not configured) select per integration (SES, Nuki, Unifi).
 * All filters are server-side via URL params and reset `page`.
 */
export function AdminPropertiesFilterPanel({
  placeholder,
  inDrawer = false,
}: AdminPropertiesFilterPanelProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  // Mirrors the URL params plus any navigation we have issued that React Router has
  // not committed yet, so a debounced write doesn't drop an in-flight one.
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

  const integrationData = [
    { value: "all", label: t("filters.integrationAny") },
    { value: "configured", label: t("filters.integrationConfigured") },
    { value: "not_configured", label: t("filters.integrationNotConfigured") },
  ];

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

      <TextInput
        placeholder={placeholder ?? t("filters.searchPlaceholder")}
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

      {INTEGRATION_FILTERS.map(({ param, label }) => {
        const raw = searchParams.get(param);
        const value = raw && VALID_FILTER_VALUES.includes(raw) ? raw : "all";
        return (
          <div key={param} className="space-y-2">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
              {label}
            </p>
            <Select
              value={value}
              onChange={(v) =>
                writeParams({ [param]: v && v !== "all" ? v : null })
              }
              data={integrationData}
              size="sm"
              radius="md"
              allowDeselect={false}
              styles={{ input: { backgroundColor: "white", fontSize: "12px" } }}
            />
          </div>
        );
      })}
    </div>
  );
}
