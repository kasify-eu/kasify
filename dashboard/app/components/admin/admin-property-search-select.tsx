import { useEffect, useState } from "react";
import { useFetcher } from "react-router";
import { Select } from "@mantine/core";
import { IconSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type {
  AdminPropertiesPageResponse,
  AdminProperty,
} from "~/types/admin-lists";

// Cap on dropdown options, both the initial list and search results — the dropdown
// is a search box, not a browsing list.
const MAX_PROPERTY_OPTIONS = 10;

interface AdminPropertySearchSelectProps {
  /**
   * Initial options (first page plus the selected property, see
   * fetchPropertyOptions in ~/lib/admin-property-options); typing in the
   * dropdown searches the backend instead.
   */
  properties: AdminProperty[];
  /** Selected property id (usually mirroring the URL param), or null for no filter. */
  value: string | null;
  onChange: (propertyId: string | null) => void;
}

/**
 * Admin property filter: a clearable, searchable Select that queries the
 * /admin/properties route loader as the user types (there can be far more
 * properties than a dropdown can hold). Empty value means "no filter".
 */
export function AdminPropertySearchSelect({
  properties,
  value,
  onChange,
}: AdminPropertySearchSelectProps) {
  const { t } = useTranslation();

  const propertiesFetcher = useFetcher<{ data: AdminPropertiesPageResponse }>();
  const [propertySearch, setPropertySearch] = useState("");

  useEffect(() => {
    const query = propertySearch.trim();
    if (!query) return;
    const timer = setTimeout(() => {
      propertiesFetcher.load(
        `/admin/properties?query=${encodeURIComponent(query)}`,
      );
    }, 300);
    return () => clearTimeout(timer);
  }, [propertySearch]);

  const searching = propertySearch.trim().length > 0;
  const searchResults = (
    searching && propertiesFetcher.data
      ? propertiesFetcher.data.data.items
      : properties
  ).slice(0, MAX_PROPERTY_OPTIONS);
  // The selected property must stay in the option list or the Select renders blank.
  const selectedPropertyItem = properties.find((p) => p.id === value);
  const optionItems =
    selectedPropertyItem &&
    !searchResults.some((p) => p.id === selectedPropertyItem.id)
      ? [...searchResults, selectedPropertyItem]
      : searchResults;

  const propertyData = optionItems.map((p) => ({
    value: p.id,
    label: p.city ? `${p.name} — ${p.city}` : p.name,
  }));

  return (
    <Select
      value={value}
      onChange={onChange}
      data={propertyData}
      placeholder={t("filters.searchPropertyPlaceholder")}
      searchable
      clearable
      searchValue={propertySearch}
      onSearchChange={setPropertySearch}
      // Options come pre-filtered from the backend; Mantine must not re-filter
      // them locally (it would hide the still-loading previous result set).
      filter={({ options }) => options}
      nothingFoundMessage={
        propertiesFetcher.state !== "idle"
          ? t("filters.searching")
          : t("filters.noProperties")
      }
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
  );
}
