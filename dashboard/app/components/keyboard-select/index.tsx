import { Select, type SelectProps } from "@mantine/core";
import { useEffect, useMemo, useState } from "react";

export interface KeyboardSelectOption {
  value: string;
  label: string;
}

export interface KeyboardSelectProps
  extends Omit<
    SelectProps,
    | "data"
    | "value"
    | "onChange"
    | "searchValue"
    | "onSearchChange"
    | "selectFirstOptionOnChange"
    | "onBlur"
  > {
  data: KeyboardSelectOption[];
  value: string | null;
  onChange: (v: string | null) => void;
  /** Called when the search input changes. Useful for server-side filtering. */
  onSearchChange?: (v: string) => void;
}

/**
 * Wraps Mantine's Select so that typing a value and tabbing away commits it.
 *
 * Mantine's Select keeps the search input separate from the selected value:
 * `selectFirstOptionOnChange` only highlights the first match, it never fires
 * onChange. Without this wrapper the user would have to press Enter to confirm,
 * which feels unnatural after typing a value.
 */
export function KeyboardSelect({
  data,
  value,
  onChange,
  onSearchChange,
  searchable = true,
  filter,
  ...rest
}: KeyboardSelectProps) {
  const labelForValue = useMemo(
    () => data.find((o) => o.value === value)?.label ?? "",
    [data, value],
  );
  const [search, setSearch] = useState(labelForValue);

  // Keep search in sync when the controlled value changes externally
  // (parent reset, programmatic update, dependent options changing, etc.).
  useEffect(() => {
    setSearch(labelForValue);
  }, [labelForValue]);

  const handleSearchChange = (v: string) => {
    setSearch(v);
    onSearchChange?.(v);
  };

  const handleBlur = () => {
    const q = search.trim();
    if (!q) {
      if (value !== null && value !== "") onChange(null);
      setSearch("");
      return;
    }
    if (q === labelForValue) return;

    // Honor the same filter as the dropdown so blur-commit matches what
    // the user sees highlighted (e.g. accent-insensitive search).
    const filtered = filter
      ? (filter({ options: data, search: q, limit: Infinity }) as KeyboardSelectOption[])
      : data.filter((o) => o.label.toLowerCase().includes(q.toLowerCase()));

    if (filtered.length === 0) {
      setSearch(labelForValue);
      return;
    }

    const lower = q.toLowerCase();
    const exact = filtered.find(
      (o) => o.label.toLowerCase() === lower || o.value === q,
    );
    const candidate = exact ?? filtered[0];
    if (candidate.value !== value) onChange(candidate.value);
    setSearch(candidate.label);
  };

  return (
    <Select
      data={data}
      value={value}
      onChange={(v) => {
        onChange(v);
        setSearch(data.find((o) => o.value === v)?.label ?? "");
      }}
      searchable={searchable}
      filter={filter}
      {...(searchable
        ? {
            searchValue: search,
            onSearchChange: handleSearchChange,
            selectFirstOptionOnChange: true,
            onBlur: handleBlur,
          }
        : {})}
      {...rest}
    />
  );
}
