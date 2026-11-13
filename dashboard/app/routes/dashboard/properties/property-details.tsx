import { useState } from "react";
import {
  ApiClient,
  ApiClientError,
  createApiConfig,
  handleApiError,
} from "~/lib/api-client";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { Link, useLoaderData, useNavigate, useRevalidator, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import {
  ActionIcon,
  Box,
  Button,
  Container,
  Group,
  Menu,
  Modal,
  Pagination,
  Space,
  Stack,
  Text,
} from "@mantine/core";
import {
  IconArrowLeft,
  IconDotsVertical,
  IconLogout,
  IconPencil,
  IconTrash,
  IconUserPlus,
} from "@tabler/icons-react";
import type { Route } from "./+types/property-details";
import { isManagerOrAbove, PropertyAccessLevel } from "~/types/properties";
import type { PropertyMember, SmartLock } from "~/types/properties";
import { ContentHeader } from "~/components/content-header";
import { SmartLockCard } from "~/components/smart-lock-card";
import { CrewMemberCard } from "~/components/crew-member-card";
import { HeaderSection } from "~/components/header-section";
import { SecondaryButton } from "~/components/secondary-button/secondary-button";
import { CrewInviteModal } from "~/components/crew-invite-modal";
import { BookingDetailsPanel } from "~/components/booking-details-panel";
import { TaskDetailsPanel } from "~/components/task-details-panel";
import { MainButton } from "~/components/main-button/main-button";
import { ActivitiesTable } from "~/components/activities/activities-table";
import { ActivitiesCardList } from "~/components/activities/activities-card-list";
import type { Activity } from "~/types/activities";

const ACTIVITIES_PAGE_SIZE = 6;

export async function loader({ request, params }: Route.LoaderArgs) {
  const propertyId = params.id;

  if (!propertyId) {
    throw new Response("Property ID is required", { status: 400 });
  }

  const url = new URL(request.url);
  const activitiesPage = parseInt(url.searchParams.get("activitiesPage") || "0", 10);

  const apiClient = await ApiClient.createAuthenticated(
    request,
    createApiConfig(),
  );

  let property;
  try {
    const response = await apiClient.getProperty(propertyId);
    property = response.data;
  } catch (error) {
    const apiError = handleApiError(error);
    if (apiError.status === 401 || apiError.status === 403) {
      return {
        property: null,
        smartLocks: null,
        activities: [],
        activitiesTotal: 0,
        activitiesPage: 0,
        accessDenied: true,
        error: null,
      };
    }
    return {
      property: null,
      smartLocks: null,
      activities: [],
      activitiesTotal: 0,
      activitiesPage: 0,
      accessDenied: false,
      error: apiError.message,
    };
  }

  let smartLocks = null;
  // smart_locks is only serialized for owners/managers (and omitted when empty),
  // so gate on role rather than field presence.
  if (isManagerOrAbove(property.role)) {
    try {
      const smartlocksResponse =
        await apiClient.getPropertySmartLocks(propertyId);
      smartLocks = smartlocksResponse.data;
    } catch {
      // Failed to fetch smart lock details — skip silently
    }
  }

  let activities: Activity[] = [];
  let activitiesTotal = 0;
  try {
    const activitiesResponse = await apiClient.getActivities(activitiesPage, ACTIVITIES_PAGE_SIZE, propertyId);
    activities = activitiesResponse.data.activities;
    activitiesTotal = activitiesResponse.data.total;
  } catch {
    // User may not have access to activities — skip silently
  }

  return {
    property,
    smartLocks,
    activities,
    activitiesTotal,
    activitiesPage,
    accessDenied: false,
    error: null,
  };
}

export default function PropertyDetails() {
  const { property, smartLocks, activities, activitiesTotal, activitiesPage, accessDenied, error } =
    useLoaderData<typeof loader>();
  const navigate = useNavigate();
  const revalidator = useRevalidator();
  const [searchParams] = useSearchParams();
  const { t } = useTranslation();
  const totalActivityPages = Math.ceil(activitiesTotal / ACTIVITIES_PAGE_SIZE);
  const [inviteModalOpened, setInviteModalOpened] = useState(false);
  const [selectedBookingId, setSelectedBookingId] = useState<string | null>(
    null,
  );
  const [panelOpened, setPanelOpened] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [taskPanelOpened, setTaskPanelOpened] = useState(false);
  const [deleteModalOpened, setDeleteModalOpened] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [leaveModalOpened, setLeaveModalOpened] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [memberToRemove, setMemberToRemove] = useState<PropertyMember | null>(
    null,
  );
  const [removingMember, setRemovingMember] = useState(false);

  const handleViewBooking = (bookingId: string) => {
    setSelectedBookingId(bookingId);
    setPanelOpened(true);
  };

  const handleClosePanel = () => {
    setPanelOpened(false);
    setSelectedBookingId(null);
  };

  const handleViewTask = (taskId: string) => {
    setSelectedTaskId(taskId);
    setTaskPanelOpened(true);
  };

  const handleCloseTaskPanel = () => {
    setTaskPanelOpened(false);
    setSelectedTaskId(null);
  };

  const handleRemoveMember = async () => {
    if (!property || !memberToRemove) return;

    setRemovingMember(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.removeCrewMember(property.id, memberToRemove.id);
      setMemberToRemove(null);
      revalidator.revalidate();
    } catch (err) {
      console.error("Failed to remove team member:", err);
    } finally {
      setRemovingMember(false);
    }
  };

  const handleDeleteProperty = async () => {
    if (!property) return;

    setDeleting(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.deleteProperty(property.id);
      navigate("/properties");
    } catch (err) {
      console.error("Failed to delete property:", err);
      setDeleting(false);
    }
  };

  const handleLeaveProperty = async () => {
    if (!property) return;

    setLeaving(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.leaveProperty(property.id);
      showSuccessToast(
        t("propertyDetails.leaveSuccess", { name: property.name }),
      );
      navigate("/properties");
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `propertyDetails.error.${code}` : null;
      showErrorToast(
        key && t(key) !== key ? t(key) : t("propertyDetails.leaveError"),
      );
      setLeaving(false);
    }
  };

  if (accessDenied) {
    return (
      <div>
        <Link to="/properties">
          <Button variant="light" leftSection={<IconArrowLeft size={16} />}>
            {t("propertyDetails.backToProperties")}
          </Button>
        </Link>
        <h1>{t("propertyDetails.accessDenied")}</h1>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        <Link to="/properties">
          <Button variant="light" leftSection={<IconArrowLeft size={16} />}>
            {t("propertyDetails.backToProperties")}
          </Button>
        </Link>
        <h1>Error: {error}</h1>
      </div>
    );
  }

  if (!property) {
    return (
      <div>
        <Link to="/properties">
          <Button variant="light" leftSection={<IconArrowLeft size={16} />}>
            {t("propertyDetails.backToProperties")}
          </Button>
        </Link>
        <h1>{t("propertyDetails.notFound")}</h1>
      </div>
    );
  }

  const canManageProperty = isManagerOrAbove(property.role);
  const isOwner = property.role === PropertyAccessLevel.Owner;

  const breadcrumbItems = [
    { title: "Properties", href: "/properties" },
    { title: property.name, href: "#" },
  ];

  const addressParts = [
    property.address,
    property.city,
    property.country,
  ].filter(Boolean);
  const subtitle =
    addressParts.length > 0 ? addressParts.join(", ") : undefined;

  return (
    <Container fluid={true}>
      <ContentHeader
        breadcrumbs={breadcrumbItems}
        subtitle={subtitle}
        right={
          property.role ? (
            <div className="flex items-center gap-2">
              {canManageProperty && (
                <>
                  <MainButton
                    leftSection={<IconPencil size={18} />}
                    onClick={() =>
                      navigate(`/properties/${property.id}/settings`)
                    }
                    visibleFrom="sm"
                  >
                    {t("propertyDetails.settings")}
                  </MainButton>
                  <ActionIcon
                    variant="filled"
                    color="dark"
                    size="lg"
                    radius="xl"
                    onClick={() =>
                      navigate(`/properties/${property.id}/settings`)
                    }
                    hiddenFrom="sm"
                  >
                    <IconPencil size={18} />
                  </ActionIcon>
                </>
              )}
              <Menu shadow="md" width={200} position="bottom-end">
                <Menu.Target>
                  <SecondaryButton style={{ paddingLeft: 12, paddingRight: 12 }}>
                    <IconDotsVertical size={16} />
                  </SecondaryButton>
                </Menu.Target>
                <Menu.Dropdown>
                  {isOwner ? (
                    <Menu.Item
                      color="red"
                      leftSection={<IconTrash size={16} />}
                      onClick={() => setDeleteModalOpened(true)}
                    >
                      {t("propertyDetails.deleteProperty")}
                    </Menu.Item>
                  ) : (
                    <Menu.Item
                      color="red"
                      leftSection={<IconLogout size={16} />}
                      onClick={() => setLeaveModalOpened(true)}
                    >
                      {t("propertyDetails.leaveProperty")}
                    </Menu.Item>
                  )}
                </Menu.Dropdown>
              </Menu>
            </div>
          ) : undefined
        }
      />

      <HeaderSection label={t("propertyDetails.upcoming")} />
      {activities && activities.length > 0 ? (
        <>
          <div className="hidden sm:block">
            <ActivitiesTable
              showHeader={false}
              activities={activities}
              onBookingClick={handleViewBooking}
              onTaskClick={handleViewTask}
            />
          </div>
          <div className="block sm:hidden">
            <ActivitiesCardList
              activities={activities}
              onBookingClick={handleViewBooking}
              onTaskClick={handleViewTask}
            />
          </div>
          {totalActivityPages > 1 && (
            <Group justify="center" mt="md">
              <Pagination
                total={totalActivityPages}
                value={activitiesPage + 1}
                size="xs"
                onChange={(newPage) => {
                  const params = new URLSearchParams(searchParams);
                  params.set("activitiesPage", String(newPage - 1));
                  navigate(`?${params.toString()}`);
                }}
              />
            </Group>
          )}
        </>
      ) : (
        <Text c="dimmed" size="sm">
          {t("propertyDetails.noActivities")}
        </Text>
      )}

      {smartLocks !== null && (
        <>
          <Space h="xl" />

          <HeaderSection label={t("propertyDetails.smartLocks")} />

          {smartLocks.length > 0 ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 3xl:grid-cols-6 gap-8">
              {smartLocks.map((smartlock: SmartLock) => (
                <SmartLockCard
                  key={smartlock.id}
                  smartLock={smartlock}
                  onUnlock={() => {}}
                  onLock={() => {}}
                />
              ))}
            </div>
          ) : (
            <Text c="dimmed" size="sm">
              {t("propertyDetails.noSmartLocks")}
            </Text>
          )}
        </>
      )}

      {canManageProperty && (
        <>
          <Space h="xl" />

          <HeaderSection label={t("propertyDetails.team")} />

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 3xl:grid-cols-6 gap-8">
            {(property.team ?? []).map((member: PropertyMember) => (
              <CrewMemberCard
                key={member.id}
                crewMember={member}
                onRemove={
                  canManageProperty
                    ? () => setMemberToRemove(member)
                    : undefined
                }
              />
            ))}
            {/* Invite Crew Member Card */}
            <div
                onClick={() => setInviteModalOpened(true)}
                className="bg-slate-50 border-2 border-dashed border-slate-200 rounded-3xl p-8 flex flex-col items-center justify-center text-center group hover:bg-white  transition-all duration-300 cursor-pointer min-h-[140px]"
            >
              <div className="w-12 h-12 rounded-full bg-white group-hover:bg-slate-800 flex items-center justify-center mb-3 shadow-sm transition-colors">
                <IconUserPlus
                    size={24}
                    stroke={1.5}
                    className="text-slate-400 group-hover:text-white transition-colors"
                />
              </div>
              <p className="text-sm font-bold text-slate-900 ">
                {t("propertyDetails.inviteCrew")}
              </p>
              <p className="text-xs text-slate-500 mt-1">
                {t("propertyDetails.inviteCrewDescription")}
              </p>
            </div>
          </div>
        </>
      )}

      <CrewInviteModal
        opened={inviteModalOpened}
        onClose={() => setInviteModalOpened(false)}
        propertyId={property.id}
      />

      <BookingDetailsPanel
        bookingId={selectedBookingId}
        opened={panelOpened}
        onClose={handleClosePanel}
      />

      <TaskDetailsPanel
        taskId={selectedTaskId}
        opened={taskPanelOpened}
        onClose={handleCloseTaskPanel}
      />

      <Modal
        opened={memberToRemove !== null}
        onClose={() => setMemberToRemove(null)}
        withCloseButton={false}
        centered
        size="md"
        radius={32}
        padding={0}
        styles={{
          content: {
            borderRadius: 32,
            overflow: "hidden",
          },
        }}
      >
        <Box className="p-8">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <IconTrash size={24} className="text-red-600" />
              </div>
              <Text size="xl" fw={700} className="tracking-tight text-slate-900">
                {t("propertyDetails.removeMemberConfirmTitle")}
              </Text>
            </div>
            <button
              onClick={() => setMemberToRemove(null)}
              className="p-2 hover:bg-slate-100 rounded-full transition-colors"
            >
              <svg
                className="w-5 h-5 text-slate-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>

          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {t("propertyDetails.removeMemberConfirmBody", {
                name: memberToRemove
                  ? [memberToRemove.name, memberToRemove.last_name]
                      .filter(Boolean)
                      .join(" ")
                  : "",
              })}
            </Text>

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={() => setMemberToRemove(null)}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("common.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={removingMember}
                onClick={handleRemoveMember}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("propertyDetails.removeMember")}
              </Button>
            </div>
          </Stack>
        </Box>
      </Modal>

      <Modal
        opened={deleteModalOpened}
        onClose={() => setDeleteModalOpened(false)}
        withCloseButton={false}
        centered
        size="md"
        radius={32}
        padding={0}
        styles={{
          content: {
            borderRadius: 32,
            overflow: "hidden",
          },
        }}
      >
        <Box className="p-8">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <IconTrash size={24} className="text-red-600" />
              </div>
              <div>
                <Text
                  size="xl"
                  fw={700}
                  className="tracking-tight text-slate-900 dark:text-white"
                >
                  {t("propertyDetails.deleteProperty")}
                </Text>
              </div>
            </div>
            <button
              onClick={() => setDeleteModalOpened(false)}
              className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors"
            >
              <svg
                className="w-5 h-5 text-slate-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>

          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {t("propertyDetails.deleteConfirmMessage", { name: property.name })}
            </Text>

            <div className="bg-red-50 border border-red-200 rounded-xl p-4">
              <Text size="sm" fw={600} c="red.7" mb={8}>
                {t("propertyDetails.deletePermanently")}
              </Text>
              <ul className="text-sm text-red-700 space-y-1 ml-4 list-disc">
                <li>{t("propertyDetails.deleteItem1")}</li>
                <li>{t("propertyDetails.deleteItem2")}</li>
                <li>{t("propertyDetails.deleteItem3")}</li>
              </ul>
            </div>

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={() => setDeleteModalOpened(false)}
                styles={{
                  root: {
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("propertyDetails.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={deleting}
                onClick={handleDeleteProperty}
                styles={{
                  root: {
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("propertyDetails.deleteProperty")}
              </Button>
            </div>
          </Stack>
        </Box>
      </Modal>

      <Modal
        opened={leaveModalOpened}
        onClose={() => setLeaveModalOpened(false)}
        withCloseButton={false}
        centered
        size="md"
        radius={32}
        padding={0}
        styles={{
          content: {
            borderRadius: 32,
            overflow: "hidden",
          },
        }}
      >
        <Box className="p-8">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <IconLogout size={24} className="text-red-600" />
              </div>
              <div>
                <Text
                  size="xl"
                  fw={700}
                  className="tracking-tight text-slate-900 dark:text-white"
                >
                  {t("propertyDetails.leaveProperty")}
                </Text>
              </div>
            </div>
            <button
              onClick={() => setLeaveModalOpened(false)}
              className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors"
            >
              <svg
                className="w-5 h-5 text-slate-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>

          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {t("propertyDetails.leaveConfirmMessage", {
                name: property.name,
              })}
            </Text>

            <div className="bg-red-50 border border-red-200 rounded-xl p-4">
              <Text size="sm" fw={600} c="red.7" mb={8}>
                {t("propertyDetails.leaveWarningTitle")}
              </Text>
              <Text size="sm" c="red.7">
                {t("propertyDetails.leaveWarningBody")}
              </Text>
            </div>

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={() => setLeaveModalOpened(false)}
                styles={{
                  root: {
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("propertyDetails.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={leaving}
                onClick={handleLeaveProperty}
                styles={{
                  root: {
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("propertyDetails.leaveProperty")}
              </Button>
            </div>
          </Stack>
        </Box>
      </Modal>
    </Container>
  );
}
