import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import { useLoaderData, useNavigate, useRevalidator, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import { ActionIcon, Box, Container, Drawer, Group, Pagination } from "@mantine/core";
import { IconFilter, IconX } from "@tabler/icons-react";
import type { Route } from "./+types/index";
import { LoaderError } from "~/components/loader-error";
import { useState } from "react";
import { ContentHeader } from "~/components/content-header";
import { EmptyState } from "~/components/empty-state/empty-state";
import { GuestCardList } from "~/components/guests/guest-card-list";
import { GuestsTable } from "~/components/guests/guests-table";
import { GuestsFilterPanel } from "~/components/guests/guests-filter-panel";
import { GuestDetailsDrawer } from "~/components/guest-details-drawer";
import { BookingDetailsPanel } from "~/components/booking-details-panel";
import type { Guest } from "~/types/bookings";

const PAGE_SIZE = 15;

export async function loader({ request }: Route.LoaderArgs) {
  try {
    const url = new URL(request.url);
    const page = parseInt(url.searchParams.get("page") || "0", 10);
    const propertyId = url.searchParams.get("property_id") || undefined;
    const query = url.searchParams.get("query") || undefined;

    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const [guests, propertiesResponse] = await Promise.all([
      apiClient.getGuests({ propertyId, query, page, size: PAGE_SIZE }),
      apiClient.getProperties(),
    ]);
    const properties = propertiesResponse.data.properties;
    return {
      guests: guests.data.guests,
      total: guests.data.total,
      hasProperties: properties.length > 0,
      properties,
      page,
      error: null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return { guests: [], total: 0, hasProperties: true, properties: [], page: 0, error: apiError.message };
  }
}

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Guests" }];
}

export default function DashboardGuests() {
  const { guests, total, page, hasProperties, properties, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);
  const [selectedGuest, setSelectedGuest] = useState<Guest | null>(null);
  const [selectedBookingId, setSelectedBookingId] = useState<string | null>(null);
  const [bookingPanelOpened, setBookingPanelOpened] = useState(false);
  const navigate = useNavigate();
  const { revalidate } = useRevalidator();
  const [searchParams] = useSearchParams();

  const handleBookingClick = (bookingId: string) => {
    setSelectedBookingId(bookingId);
    setBookingPanelOpened(true);
  };

  const handleCloseBookingPanel = () => {
    setBookingPanelOpened(false);
    setSelectedBookingId(null);
  };

  const totalPages = Math.ceil(total / PAGE_SIZE);
  const hasActiveFilters = ["property_id", "query"].some(
    (key) => searchParams.has(key),
  );

  if (error) {
    return <LoaderError title={t("guests.title")} />;
  }

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/guests?${params.toString()}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`${t("guests.title")} (${total})`}
        subtitle={t("guests.subtitle")}
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
            </Group>
          ) : undefined
        }
      />

      {!hasProperties ? (
        <EmptyState
          title={t("guests.emptyNoPropertiesTitle")}
          subtitle={t("guests.emptyNoPropertiesSubtitle")}
          image="/icons/briefcase-metal.svg"
          button={{ name: t("common.addFirstProperty"), url: "/properties/new" }}
        />
      ) : guests.length === 0 && !hasActiveFilters ? (
        <EmptyState
          title={t("guests.emptyTitle")}
          image="/icons/briefcase-metal.svg"
          subtitle={t("guests.emptySubtitle")}
        />
      ) : (
        <div className="flex gap-6 items-start">
          <div className="flex-1 min-w-0">
            <div className="block md:hidden">
              <GuestCardList
                guests={guests}
                onGuestClick={setSelectedGuest}
                onBookingClick={handleBookingClick}
              />
            </div>
            <div className="hidden md:block">
              <GuestsTable
                guests={guests}
                properties={properties}
                onGuestClick={setSelectedGuest}
                onBookingClick={handleBookingClick}
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
            <GuestsFilterPanel properties={properties} />
          </Box>
        </div>
      )}

      <GuestDetailsDrawer
        guest={selectedGuest}
        opened={selectedGuest !== null}
        onClose={() => setSelectedGuest(null)}
      />

      <BookingDetailsPanel
        bookingId={selectedBookingId}
        opened={bookingPanelOpened}
        onClose={handleCloseBookingPanel}
        onUpdate={revalidate}
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
          <GuestsFilterPanel properties={properties} inDrawer />
        </Box>
      </Drawer>
    </Container>
  );
}
