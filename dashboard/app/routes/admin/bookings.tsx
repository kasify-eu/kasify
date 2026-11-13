import { useState } from "react";
import { useLoaderData, useNavigate, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
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
import {
  BookingChannelStatusIcons,
  BookingComplianceIcons,
} from "~/components/bookings/bookings-table";
import { AdminBookingsFilterPanel } from "~/components/admin/admin-bookings-filter-panel";
import { BookingDetailsPanel } from "~/components/booking-details-panel";
import { SesBookingDrawer } from "~/components/admin/ses-booking-drawer";
import { handleAdminSesAction } from "~/lib/admin-ses-action";
import type { AdminProperty } from "~/types/admin-lists";
import type { Booking } from "~/types/bookings";
import type { Route } from "./+types/bookings";

const PAGE_SIZE = 20;

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify Admin | Bookings" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const pageRaw = Number.parseInt(url.searchParams.get("page") ?? "0", 10);
  const page = Number.isFinite(pageRaw) && pageRaw >= 0 ? pageRaw : 0;

  // When no date range is set in the URL, default to the next 90 days (including
  // bookings already in progress — the backend filters by stay overlap, so `from =
  // today` keeps stays that started earlier but still overlap). Only apply when both
  // params are absent; if the user set either, respect the explicit URL values.
  // `dates=all` opts out of the default entirely (no date filter → all bookings).
  const fromParam = url.searchParams.get("from");
  const toParam = url.searchParams.get("to");
  const allDates = url.searchParams.get("dates") === "all";
  const usingDefaultRange = !fromParam && !toParam && !allDates;
  const from = usingDefaultRange
    ? dayjs().format("YYYY-MM-DD")
    : fromParam || undefined;
  const to = usingDefaultRange
    ? dayjs().add(90, "day").format("YYYY-MM-DD")
    : toParam || undefined;

  const propertyId = url.searchParams.get("property_id") || undefined;

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const [response, propertyOptions] = await Promise.all([
      apiClient.getAdminBookings({
        from,
        to,
        query: url.searchParams.get("query") || undefined,
        channel: url.searchParams.get("channel") || undefined,
        property_id: propertyId,
        page,
        size: PAGE_SIZE,
      }),
      // Properties only feed the filter dropdown — a failure there must not
      // blank the bookings table, so it degrades to an empty dropdown instead.
      fetchPropertyOptions(apiClient, propertyId).catch(() => null),
    ]);
    return {
      data: response.data,
      properties: propertyOptions ?? ([] as AdminProperty[]),
      page,
      defaultFrom: usingDefaultRange ? from : null,
      defaultTo: usingDefaultRange ? to : null,
      error: null as string | null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return {
      data: { items: [] as Booking[], page: 0, size: PAGE_SIZE, total: 0 },
      properties: [] as AdminProperty[],
      page,
      defaultFrom: usingDefaultRange ? from : null,
      defaultTo: usingDefaultRange ? to : null,
      error: apiError.message,
    };
  }
}

// SES submit/anular/refresh actions posted by the SES details drawer.
export async function action({ request }: Route.ActionArgs) {
  return handleAdminSesAction(request);
}

function formatDate(dateStr: string | null, withYear: boolean): string {
  if (!dateStr) return "—";
  const date = new Date(dateStr);
  if (isNaN(date.getTime())) return dateStr ?? "—";
  return date.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    ...(withYear ? { year: "numeric" } : {}),
  });
}

/** "Jul 11 — Jul 15, 2026" for same-year stays, "Jul 8, 2026 — Jan 8, 2027" across years. */
function formatDateRange(from: string | null, to: string | null): string {
  const sameYear =
    !!from &&
    !!to &&
    new Date(from).getFullYear() === new Date(to).getFullYear();
  return `${formatDate(from, !sameYear)} — ${formatDate(to, true)}`;
}

export default function AdminBookings() {
  const { data, properties, page, defaultFrom, defaultTo, error } =
    useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);
  const [selectedBookingId, setSelectedBookingId] = useState<string | null>(
    null,
  );
  const [panelOpened, setPanelOpened] = useState(false);
  const [sesBookingId, setSesBookingId] = useState<string | null>(null);

  const handleViewBooking = (bookingId: string) => {
    setSelectedBookingId(bookingId);
    setPanelOpened(true);
  };

  const handleClosePanel = () => {
    setPanelOpened(false);
    setSelectedBookingId(null);
  };

  if (error) {
    return (
      <Container fluid={true}>
        <ContentHeader title="Bookings" subtitle="Error loading bookings" />
        <p className="text-red-500">{error}</p>
      </Container>
    );
  }

  const items = data.items;
  const totalPages = Math.max(1, Math.ceil(data.total / PAGE_SIZE));

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/admin/bookings?${params.toString()}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`Bookings (${data.total})`}
        subtitle="All bookings across accounts"
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
                      Booking
                    </th>
                    <th
                      className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider"
                      style={{ width: 180 }}
                    >
                      Dates
                    </th>
                    <th
                      className="px-2.5 py-4 w-px whitespace-nowrap"
                      aria-label="Channel and booking status"
                    />
                    <th
                      className="px-2.5 py-4 w-px whitespace-nowrap"
                      aria-label="Check-in and SES status"
                    />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {items.length === 0 && (
                    <tr>
                      <td
                        colSpan={5}
                        className="px-6 py-12 text-center text-sm text-slate-400"
                      >
                        No bookings match the current filter
                      </td>
                    </tr>
                  )}
                  {items.map((booking) => (
                    <tr
                      key={booking.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => handleViewBooking(booking.id)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          handleViewBooking(booking.id);
                        }
                      }}
                      className="group hover:bg-slate-50 transition-colors cursor-pointer"
                    >
                      <td className="px-2.5 py-4" style={{ width: 240 }}>
                        <p className="font-semibold text-xs text-slate-900 truncate max-w-[220px]">
                          {booking.property.name}
                        </p>
                        <p className="text-xs text-slate-400 truncate max-w-[220px]">
                          {booking.property.city ?? "—"}
                        </p>
                      </td>
                      <td className="px-2.5 py-4">
                        <p className="font-semibold text-xs text-slate-900 truncate max-w-[120px]">
                          {booking.channel_id}
                        </p>
                        <p className="text-xs text-slate-400 truncate max-w-[120px]">
                          {`${booking.guest_first_name ?? ""} ${booking.guest_last_name ?? ""}`.trim() ||
                            "—"}
                        </p>
                      </td>
                      <td
                        className="px-2.5 py-4 whitespace-nowrap"
                        style={{ width: 180 }}
                      >
                        <span className="text-xs text-slate-700">
                          {formatDateRange(booking.from, booking.to)}
                        </span>
                      </td>
                      <td className="px-2 py-4 whitespace-nowrap">
                        <BookingChannelStatusIcons booking={booking} />
                      </td>
                      <td className="px-2 py-4 whitespace-nowrap">
                        <BookingComplianceIcons
                          booking={booking}
                          onSesClick={() => setSesBookingId(booking.id)}
                        />
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
          <AdminBookingsFilterPanel
            properties={properties}
            defaultFrom={defaultFrom}
            defaultTo={defaultTo}
          />
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
          <AdminBookingsFilterPanel
            inDrawer
            properties={properties}
            defaultFrom={defaultFrom}
            defaultTo={defaultTo}
          />
        </Box>
      </Drawer>

      <BookingDetailsPanel
        admin
        bookingId={selectedBookingId}
        opened={panelOpened}
        onClose={handleClosePanel}
      />

      <SesBookingDrawer
        bookingId={sesBookingId}
        onClose={() => setSesBookingId(null)}
      />
    </Container>
  );
}
