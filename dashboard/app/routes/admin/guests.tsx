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
import { IconFilter, IconX } from "@tabler/icons-react";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import { fetchPropertyOptions } from "~/lib/admin-property-options";
import { ContentHeader } from "~/components/content-header";
import { AdminGuestsFilterPanel } from "~/components/admin/admin-guests-filter-panel";
import { GuestDetailsDrawer } from "~/components/guest-details-drawer";
import type { Guest } from "~/types/bookings";
import {
  adminGuestToGuest,
  type AdminGuest,
  type AdminProperty,
} from "~/types/admin-lists";
import type { Route } from "./+types/guests";

const PAGE_SIZE = 20;

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify Admin | Guests" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const pageRaw = Number.parseInt(url.searchParams.get("page") ?? "0", 10);
  const page = Number.isFinite(pageRaw) && pageRaw >= 0 ? pageRaw : 0;
  const query = url.searchParams.get("query") || undefined;
  const propertyId = url.searchParams.get("property_id") || undefined;
  const channelId = url.searchParams.get("channel_id") || undefined;

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const [guestsResponse, allProperties] = await Promise.all([
      apiClient.getAdminGuests({
        query,
        property_id: propertyId,
        channel_id: channelId,
        page,
        size: PAGE_SIZE,
      }),
      // Properties only feed the filter dropdown — a failure there must not
      // blank the guests table, so it degrades to an empty dropdown instead.
      fetchPropertyOptions(apiClient, propertyId).catch(() => null),
    ]);
    return {
      data: guestsResponse.data,
      properties: allProperties ?? ([] as AdminProperty[]),
      page,
      error: null as string | null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return {
      data: { items: [] as AdminGuest[], page: 0, size: PAGE_SIZE, total: 0 },
      properties: [] as AdminProperty[],
      page,
      error: apiError.message,
    };
  }
}

function countryFlag(alpha2?: string | null): string | null {
  if (!alpha2 || alpha2.length !== 2) return null;
  const code = alpha2.toUpperCase();
  if (!/^[A-Z]{2}$/.test(code)) return null;
  const base = 0x1f1e6;
  return String.fromCodePoint(
    base + code.charCodeAt(0) - 65,
    base + code.charCodeAt(1) - 65,
  );
}

export default function AdminGuests() {
  const { data, properties, page, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);
  const [selectedGuest, setSelectedGuest] = useState<Guest | null>(null);

  if (error) {
    return (
      <Container fluid={true}>
        <ContentHeader title="Guests" subtitle="Error loading guests" />
        <p className="text-red-500">{error}</p>
      </Container>
    );
  }

  const items = data.items;
  const totalPages = Math.max(1, Math.ceil(data.total / PAGE_SIZE));

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/admin/guests?${params.toString()}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`Guests (${data.total})`}
        subtitle="All registered guests across accounts"
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
                      Guest
                    </th>
                    <th
                      className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider"
                      style={{ width: 160 }}
                    >
                      {t("filters.channelId")}
                    </th>
                    <th
                      className="px-2.5 py-4"
                      style={{ width: 32 }}
                      aria-label="Nationality"
                    />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {items.length === 0 && (
                    <tr>
                      <td
                        colSpan={4}
                        className="px-6 py-12 text-center text-sm text-slate-400"
                      >
                        No guests match the current filter
                      </td>
                    </tr>
                  )}
                  {items.map((guest) => {
                    const openGuest = () =>
                      setSelectedGuest(adminGuestToGuest(guest));
                    return (
                    <tr
                      key={guest.id}
                      role="button"
                      tabIndex={0}
                      onClick={openGuest}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          openGuest();
                        }
                      }}
                      className="group hover:bg-slate-50 transition-colors cursor-pointer"
                    >
                      <td className="px-2.5 py-4" style={{ width: 240 }}>
                        <p className="font-semibold text-xs text-slate-900 truncate max-w-[220px]">
                          {guest.property_name}
                        </p>
                        <p className="text-xs text-slate-400 truncate max-w-[220px]">
                          {guest.property_city}
                        </p>
                      </td>
                      <td className="px-2.5 py-4">
                        <p className="font-semibold text-xs text-slate-900">
                          {guest.first_name} {guest.last_name}
                          {guest.second_last_name
                            ? ` ${guest.second_last_name}`
                            : ""}
                        </p>
                      </td>
                      <td className="px-2.5 py-4" style={{ width: 160 }}>
                        <p className="text-xs text-slate-700 truncate max-w-[150px] font-mono">
                          {guest.channel_id || "—"}
                        </p>
                      </td>
                      <td className="px-2.5 py-4 text-center" style={{ width: 32 }}>
                        {(() => {
                          const flag = countryFlag(guest.nationality_alpha_2);
                          return flag ? (
                            <span
                              aria-label={guest.nationality_name ?? undefined}
                              title={guest.nationality_name ?? undefined}
                              className="text-lg leading-none"
                            >
                              {flag}
                            </span>
                          ) : (
                            <span className="text-xs text-slate-400">—</span>
                          );
                        })()}
                      </td>
                    </tr>
                    );
                  })}
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
          <AdminGuestsFilterPanel properties={properties} />
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
          <AdminGuestsFilterPanel properties={properties} inDrawer />
        </Box>
      </Drawer>

      <GuestDetailsDrawer
        guest={selectedGuest}
        opened={selectedGuest !== null}
        mode="view"
        canEdit={false}
        canDelete={false}
        onClose={() => setSelectedGuest(null)}
      />
    </Container>
  );
}
