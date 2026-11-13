import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { AdminPropertySearchSelect } from "~/components/admin/admin-property-search-select";
import type { AdminProperty } from "~/types/admin-lists";

interface AdminGuestsFilterPanelProps {
  /**
   * Initial options for the property dropdown (first page plus the selected
   * property); typing in the dropdown searches the backend instead.
   */
  properties: AdminProperty[];
  inDrawer?: boolean;
}

/**
 * Admin guests filter panel: debounced free-text search + property dropdown + debounced
 * channel-id text. All filters are server-side via URL params (`query`, `property_id`,
 * `channel_id`) and reset `page`. The property dropdown searches the backend as the user
 * types (there can be far more properties than a dropdown can hold). Separate from the
 * shared AdminSearchFilterPanel because the admin properties view reuses that one and
 * needs neither the property nor channel-id field.
 */
export function AdminGuestsFilterPanel({
  properties,
  inDrawer = false,
}: AdminGuestsFilterPanelProps) {
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

  const channelIdFromUrl = searchParams.get("channel_id") ?? "";
  const [localChannelId, setLocalChannelId] = useState(channelIdFromUrl);

  const selectedProperty = searchParams.get("property_id");

  useEffect(() => {
    setLocalSearch(queryFromUrl);
  }, [queryFromUrl]);

  useEffect(() => {
    setLocalChannelId(channelIdFromUrl);
  }, [channelIdFromUrl]);

  // Writes a param (null deletes it) to the URL, resetting page.
  const writeParam = (key: string, value: string | null) => {
    const params = new URLSearchParams(pendingParamsRef.current);
    if (value) {
      params.set(key, value);
    } else {
      params.delete(key);
    }
    params.delete("page");
    pendingParamsRef.current = params;
    navigate({ search: params.toString() ? `?${params.toString()}` : "" });
  };

  // Debounced write of a free-text param to the URL.
  const debounceParam = (key: string, localValue: string) => {
    const timer = setTimeout(() => {
      if (localValue === (pendingParamsRef.current.get(key) ?? "")) return;
      writeParam(key, localValue || null);
    }, 300);
    return () => clearTimeout(timer);
  };

  useEffect(() => debounceParam("query", localSearch), [localSearch]);
  useEffect(
    () => debounceParam("channel_id", localChannelId),
    [localChannelId],
  );

  const updateParam = (key: string, value: string | null) => {
    writeParam(key, value || null);
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

      <TextInput
        placeholder={t("filters.searchGuestPlaceholder")}
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
          {t("filters.channelId")}
        </p>
        <TextInput
          placeholder={t("filters.channelIdPlaceholder")}
          value={localChannelId}
          onChange={(e) => setLocalChannelId(e.currentTarget.value)}
          size="sm"
          radius="md"
          styles={{ input: { backgroundColor: "white", fontSize: "12px" } }}
        />
      </div>
    </div>
  );
}
