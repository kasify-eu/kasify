import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { TextInput } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

interface AdminSearchFilterPanelProps {
  placeholder?: string;
  inDrawer?: boolean;
}

/**
 * Minimal admin filter panel with a single debounced free-text search that writes to the
 * `query` URL param (and resets `page`). Used by the admin users and guests views.
 */
export function AdminSearchFilterPanel({
  placeholder,
  inDrawer = false,
}: AdminSearchFilterPanelProps) {
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

  useEffect(() => {
    const timer = setTimeout(() => {
      const params = new URLSearchParams(searchParamsRef.current);
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
    </div>
  );
}
