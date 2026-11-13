import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import dayjs from "dayjs";
import { redirect, useLoaderData, useNavigate, useRevalidator, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { ActionIcon, Box, Container, Drawer, Group, Pagination } from "@mantine/core";
import { IconFilter, IconPlus, IconX } from "@tabler/icons-react";
import { MainButton } from "~/components/main-button/main-button";
import type { Route } from "./+types/index";
import { BookingDetailsPanel } from "~/components/booking-details-panel";
import { LoaderError } from "~/components/loader-error";
import { useState } from "react";
import { ContentHeader } from "~/components/content-header";
import { EmptyState } from "~/components/empty-state/empty-state";
import { BookingCardList } from "~/components/bookings/booking-card-list";
import { BookingsTable } from "~/components/bookings/bookings-table";
import { BookingsFilterPanel } from "~/components/bookings/bookings-filter-panel";

const PAGE_SIZE = 15;

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);

  // Default to "Next 7 days" when no date range is in the URL.
  if (!url.searchParams.has("from") && !url.searchParams.has("to")) {
    const today = dayjs();
    const params = new URLSearchParams(url.searchParams);
    params.set("from", today.format("YYYY-MM-DD"));
    params.set("to", today.add(7, "day").format("YYYY-MM-DD"));
    throw redirect(`${url.pathname}?${params.toString()}`);
  }

  try {
    const page = parseInt(url.searchParams.get("page") || "0", 10);
    const from = url.searchParams.get("from") || undefined;
    const to = url.searchParams.get("to") || undefined;
    const propertyId = url.searchParams.get("property_id") || undefined;
    const query = url.searchParams.get("query") || undefined;
    const channel = url.searchParams.get("channel") || undefined;

    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const [bookings, propertiesResponse] = await Promise.all([
      apiClient.getBookings({ from, to, propertyId, query, channel, page, size: PAGE_SIZE }),
      apiClient.getProperties(),
    ]);
    const properties = propertiesResponse.data.properties;
    return {
      bookings: bookings.data.bookings,
      total: bookings.data.total,
      hasProperties: properties.length > 0,
      properties,
      page,
      error: null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return { bookings: [], total: 0, hasProperties: true, properties: [], page: 0, error: apiError.message };
  }
}

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Bookings" }];
}

export default function DashboardBookings() {
  const { bookings, total, page, hasProperties, properties, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const bookingIdParam = searchParams.get("bookingId");
  const [selectedBookingId, setSelectedBookingId] = useState<string | null>(bookingIdParam);
  const [panelOpened, setPanelOpened] = useState(!!bookingIdParam);
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);
  const navigate = useNavigate();
  const { revalidate } = useRevalidator();

  const totalPages = Math.ceil(total / PAGE_SIZE);
  const hasActiveFilters = ["from", "to", "property_id", "query", "channel"].some(
    (key) => searchParams.has(key),
  );

  if (error) {
    return <LoaderError title={t("bookings.title")} />;
  }

  const handleViewBooking = (bookingId: string) => {
    setSelectedBookingId(bookingId);
    setPanelOpened(true);
  };

  const handleClosePanel = () => {
    setPanelOpened(false);
    setSelectedBookingId(null);
    if (searchParams.has("bookingId")) {
      const params = new URLSearchParams(searchParams);
      params.delete("bookingId");
      setSearchParams(params, { replace: true });
    }
    revalidate();
  };

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/bookings?${params.toString()}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`${t("bookings.title")} (${total})`}
        subtitle={t("bookings.subtitle")}
        right={
          hasProperties ? (
            <Group gap="sm">
              <ActionIcon
                variant="default"
                size="lg"
                radius="xl"
                onClick={() => setFilterDrawerOpened(true)}
                hiddenFrom="lg"
              >
                <IconFilter size={18} />
              </ActionIcon>
              <MainButton
                leftSection={<IconPlus size={18} />}
                onClick={() => navigate("/bookings/new")}
                visibleFrom="sm"
              >
                {t("bookings.create")}
              </MainButton>
              <ActionIcon
                variant="filled"
                color="dark"
                size="lg"
                radius="xl"
                onClick={() => navigate("/bookings/new")}
                hiddenFrom="sm"
              >
                <IconPlus size={18} />
              </ActionIcon>
            </Group>
          ) : undefined
        }
      />

      {!hasProperties ? (
        <EmptyState
          title={t("bookings.emptyNoPropertiesTitle")}
          subtitle={t("bookings.emptyNoPropertiesSubtitle")}
          image="/icons/briefcase-metal.svg"
          button={{ name: t("common.addFirstProperty"), url: "/properties/new" }}
        />
      ) : bookings.length === 0 && !hasActiveFilters ? (
        <EmptyState
          title={t("bookings.emptyTitle")}
          image="/icons/briefcase-metal.svg"
          subtitle={t("bookings.emptySubtitle")}
        />
      ) : (
        <div className="flex gap-6 items-start">
          <div className="flex-1 min-w-0">
            <div className="block md:hidden">
              <BookingCardList
                bookings={bookings}
                onBookingClick={handleViewBooking}
              />
            </div>
            <div className="hidden md:block">
              <BookingsTable
                bookings={bookings}
                onBookingClick={handleViewBooking}
              />
            </div>
            {totalPages > 1 && (
              <Group justify="center" mt="xl">
                <Pagination
                  total={totalPages}
                  value={page + 1}
                  onChange={handlePageChange}
                />
              </Group>
            )}
          </div>
          <Box component="aside" visibleFrom="lg" w={240} style={{ flexShrink: 0, position: "sticky", top: 24 }}>
            <BookingsFilterPanel properties={properties} />
          </Box>
        </div>
      )}

      <BookingDetailsPanel
        bookingId={selectedBookingId}
        opened={panelOpened}
        onClose={handleClosePanel}
      />

      <Drawer
        opened={filterDrawerOpened}
        onClose={() => setFilterDrawerOpened(false)}
        position="right"
        size="xs"
        title={
          <span className="text-lg pl-2 font-semibold text-slate-800">{t("filters.title")}</span>
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
        styles={{
          header: { backgroundColor: "#F8FAFC" },
        }}
      >
      <Box pt="xl">
        <BookingsFilterPanel properties={properties} inDrawer />
      </Box>
      </Drawer>
    </Container>
  );
}
