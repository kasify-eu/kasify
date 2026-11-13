import type { ApiClient } from "~/lib/api-client";
import type { AdminProperty } from "~/types/admin-lists";

// Initial options for the admin property dropdown; typing in it searches the backend.
export const PROPERTY_OPTIONS_SIZE = 10;

/**
 * Loader-side companion of AdminPropertySearchSelect: the dropdown searches
 * server-side, so the loader only provides the first page of options — plus the
 * currently selected property (a deep-linked property_id may not be on that page,
 * and the Select needs its option present to render the label).
 */
export async function fetchPropertyOptions(
  apiClient: ApiClient,
  selectedPropertyId: string | undefined,
): Promise<AdminProperty[]> {
  const [firstPage, selected] = await Promise.all([
    apiClient.getAdminProperties({ size: PROPERTY_OPTIONS_SIZE }),
    selectedPropertyId
      ? apiClient.getAdminProperties({ property_id: selectedPropertyId })
      : null,
  ]);
  const items = [...firstPage.data.items];
  for (const property of selected?.data.items ?? []) {
    if (!items.some((item) => item.id === property.id)) {
      items.push(property);
    }
  }
  return items;
}
