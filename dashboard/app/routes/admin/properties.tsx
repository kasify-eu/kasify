import { useState } from "react";
import { useLoaderData, useNavigate, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import {
  ActionIcon,
  Box,
  Container,
  Drawer,
  Group,
  Pagination,
} from "@mantine/core";
import {
  IconCircleCheck,
  IconCircleDashed,
  IconFilter,
  IconX,
} from "@tabler/icons-react";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import { ContentHeader } from "~/components/content-header";
import { AdminPropertiesFilterPanel } from "~/components/admin/admin-properties-filter-panel";
import { PropertyDetailsPanel } from "~/components/property-details-panel";
import type {
  AdminProperty,
  IntegrationConfiguredFilter,
} from "~/types/admin-lists";
import type { Route } from "./+types/properties";

const PAGE_SIZE = 20;

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify Admin | Properties" }];
}

function integrationFilter(
  url: URL,
  param: string,
): IntegrationConfiguredFilter | undefined {
  const value = url.searchParams.get(param);
  return value === "configured" || value === "not_configured"
    ? value
    : undefined;
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const pageRaw = Number.parseInt(url.searchParams.get("page") ?? "0", 10);
  const page = Number.isFinite(pageRaw) && pageRaw >= 0 ? pageRaw : 0;
  const query = url.searchParams.get("query") || undefined;

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const response = await apiClient.getAdminProperties({
      query,
      page,
      size: PAGE_SIZE,
      ses: integrationFilter(url, "ses"),
      nuki: integrationFilter(url, "nuki"),
      unifi: integrationFilter(url, "unifi"),
    });
    return { data: response.data, page, error: null as string | null };
  } catch (error) {
    const apiError = handleApiError(error);
    return {
      data: { items: [] as AdminProperty[], page: 0, size: PAGE_SIZE, total: 0 },
      page,
      error: apiError.message,
    };
  }
}

// Tolerates a missing list so the page survives an API that predates the field
function hasVendor(property: AdminProperty, vendor: string): boolean {
  return (property.smartlock_vendors ?? []).some(
    (v) => v.toLowerCase() === vendor.toLowerCase(),
  );
}

function IntegrationBadge({
  label,
  configured,
}: {
  label: string;
  configured: boolean;
}) {
  return (
    <span className="flex items-center gap-1">
      {configured ? (
        <IconCircleCheck
          size={18}
          className="text-emerald-500"
          aria-label={`${label} configured`}
        />
      ) : (
        <IconCircleDashed
          size={18}
          className="text-slate-300"
          aria-label={`${label} not configured`}
        />
      )}
      <span
        className={
          configured ? "text-xs text-slate-700" : "text-xs text-slate-400"
        }
      >
        {label}
      </span>
    </span>
  );
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return "—";
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export default function AdminProperties() {
  const { data, page, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);
  const [selectedProperty, setSelectedProperty] = useState<AdminProperty | null>(
    null,
  );
  const [panelOpened, setPanelOpened] = useState(false);

  const handleViewProperty = (property: AdminProperty) => {
    setSelectedProperty(property);
    setPanelOpened(true);
  };

  const handleClosePanel = () => {
    setPanelOpened(false);
    setSelectedProperty(null);
  };

  if (error) {
    return (
      <Container fluid={true}>
        <ContentHeader title="Properties" subtitle="Error loading properties" />
        <p className="text-red-500">{error}</p>
      </Container>
    );
  }

  const items = data.items;
  const totalPages = Math.max(1, Math.ceil(data.total / PAGE_SIZE));

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/admin/properties?${params.toString()}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`Properties (${data.total})`}
        subtitle="All properties across accounts, with the owner that created each"
        right={
          <ActionIcon
            variant="default"
            size="lg"
            radius="xl"
            onClick={() => setFilterDrawerOpened(true)}
            hiddenFrom="lg"
          >
            <IconFilter size={18} />
          </ActionIcon>
        }
      />

      <div className="flex gap-6 items-start">
        <div className="flex-1 min-w-0">
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200">
                    <th
                      className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider"
                      style={{ width: 240 }}
                    >
                      Property
                    </th>
                    <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Owner
                    </th>
                    <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Integrations
                    </th>
                    <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Created
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {items.length === 0 && (
                    <tr>
                      <td
                        colSpan={4}
                        className="px-6 py-12 text-center text-sm text-slate-400"
                      >
                        No properties match the current filter
                      </td>
                    </tr>
                  )}
                  {items.map((property) => (
                    <tr
                      key={property.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => handleViewProperty(property)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          handleViewProperty(property);
                        }
                      }}
                      className="group hover:bg-slate-50 transition-colors cursor-pointer"
                    >
                      <td className="px-2.5 py-4" style={{ width: 240 }}>
                        <p className="font-semibold text-xs text-slate-900 truncate max-w-[220px]">
                          {property.name}
                        </p>
                        <p className="text-xs text-slate-400 truncate max-w-[220px]">
                          {property.city}
                        </p>
                      </td>
                      <td className="px-2.5 py-4">
                        <p className="text-xs text-slate-700">
                          {property.owner.name} {property.owner.last_name}
                        </p>
                        <p className="text-xs text-slate-400">
                          {property.owner.email}
                        </p>
                      </td>
                      <td className="px-2.5 py-4">
                        <div className="flex items-center gap-3">
                          <IntegrationBadge
                            label="SES"
                            configured={!!property.ses_establishment_code}
                          />
                          <IntegrationBadge
                            label="Nuki"
                            configured={hasVendor(property, "nuki")}
                          />
                          <IntegrationBadge
                            label="Unifi"
                            configured={hasVendor(property, "unifi")}
                          />
                        </div>
                      </td>
                      <td className="px-2.5 py-4">
                        <span className="text-xs text-slate-500">
                          {formatDate(property.created_at)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
          <Group justify="center" mt="xl">
            <Pagination
              total={totalPages}
              value={page + 1}
              onChange={handlePageChange}
            />
          </Group>
        </div>
        <Box
          component="aside"
          visibleFrom="lg"
          w={240}
          style={{ flexShrink: 0, position: "sticky", top: 24 }}
        >
          <AdminPropertiesFilterPanel placeholder="Search property or owner…" />
        </Box>
      </div>

      <Drawer
        opened={filterDrawerOpened}
        onClose={() => setFilterDrawerOpened(false)}
        position="right"
        size="xs"
        title={
          <span className="text-lg pl-2 font-semibold text-slate-800">
            {t("filters.title")}
          </span>
        }
        closeButtonProps={{
          icon: <IconX size={24} color="white" />,
          style: {
            marginTop: 6,
            marginBottom: 6,
            marginRight: 12,
            backgroundColor: "var(--mantine-color-dark-7)",
          },
          size: "xl",
        }}
        styles={{ header: { backgroundColor: "#F8FAFC" } }}
      >
        <Box pt="xl">
          <AdminPropertiesFilterPanel
            placeholder="Search property or owner…"
            inDrawer
          />
        </Box>
      </Drawer>

      <PropertyDetailsPanel
        property={selectedProperty}
        opened={panelOpened}
        onClose={handleClosePanel}
      />
    </Container>
  );
}
