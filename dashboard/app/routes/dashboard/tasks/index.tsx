import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import dayjs from "dayjs";
import { useState } from "react";
import { redirect, useLoaderData, useNavigate, useRevalidator, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import {
  ActionIcon,
  Box,
  Container,
  Drawer,
  Group,
  Pagination,
} from "@mantine/core";
import { IconCalendar, IconFilter, IconPlus, IconUser, IconX } from "@tabler/icons-react";
import type { Route } from "./+types/index";
import { typeBadgeClasses } from "~/components/calendar-shared/styles";
import { ContentHeader } from "~/components/content-header";
import { EmptyState } from "~/components/empty-state/empty-state";
import { LoaderError } from "~/components/loader-error";
import { MainButton } from "~/components/main-button/main-button";
import type { Task, TaskType } from "~/types/tasks";
import { TaskDetailsPanel } from "~/components/task-details-panel";
import { TasksFilterPanel } from "~/components/tasks/tasks-filter-panel";

const PAGE_SIZE = 15;

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Tasks" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);

  // Default to "Next 7 days" when no date range is in the URL.
  if (!url.searchParams.has("scheduled_from") && !url.searchParams.has("scheduled_to")) {
    const today = dayjs();
    const params = new URLSearchParams(url.searchParams);
    params.set("scheduled_from", today.format("YYYY-MM-DD"));
    params.set("scheduled_to", today.add(7, "day").format("YYYY-MM-DD"));
    throw redirect(`${url.pathname}?${params.toString()}`);
  }

  const page = parseInt(url.searchParams.get("page") || "0", 10);
  const propertyId = url.searchParams.get("property_id") || undefined;
  const taskType = url.searchParams.get("task_type") || undefined;
  const scheduledFrom = url.searchParams.get("scheduled_from") || undefined;
  const scheduledTo = url.searchParams.get("scheduled_to") || undefined;
  const assignedTo = url.searchParams.get("assigned_to") || undefined;

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );

    const [tasksResponse, propertiesResponse] = await Promise.all([
      apiClient.getTasks({
        page,
        size: PAGE_SIZE,
        property_id: propertyId,
        task_type: taskType as TaskType | undefined,
        scheduled_from: scheduledFrom,
        scheduled_to: scheduledTo,
        assigned_to: assignedTo,
      }),
      apiClient.getProperties(),
    ]);

    const properties = propertiesResponse.data.properties;

    // Crew for the "assigned" filter is lazy-loaded client-side by
    // TasksFilterPanel — fetching it here would add one request per property
    // to every navigation.
    return {
      tasks: tasksResponse.data.tasks,
      total: tasksResponse.data.total ?? 0,
      hasProperties: properties.length > 0,
      properties,
      page,
      error: null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return { tasks: [], total: 0, hasProperties: true, properties: [], page: 0, error: apiError.message };
  }
}

function getTaskTypeLabel(taskType: string | undefined, t: (key: string) => string) {
  switch (taskType) {
    case "cleaning":
      return t("tasks.type.cleaning");
    case "maintenance":
      return t("tasks.type.maintenance");
    case "pool":
      return t("tasks.type.pool");
    case "gardening":
      return t("tasks.type.gardening");
    default:
      return taskType;
  }
}

// API returns property-local datetimes labeled with `Z`. Strip the suffix so
// `new Date()` treats them as wall-clock time rather than UTC.
function parseLocal(iso: string): Date {
  return new Date(iso.replace(/[zZ]$/, "").replace(/([+-]\d{2}:?\d{2})$/, ""));
}

function formatDate(iso: string) {
  return parseLocal(iso).toLocaleDateString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
  });
}

function isToday(iso: string) {
  const d = parseLocal(iso);
  const now = new Date();
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  );
}

function formatTime(iso: string) {
  return parseLocal(iso).toLocaleTimeString(undefined, {
    hour: "numeric",
    minute: "2-digit",
  });
}

export default function TasksList() {
  const { tasks, total, page, hasProperties, properties, error } = useLoaderData<typeof loader>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const { revalidate } = useRevalidator();

  const totalPages = Math.ceil(total / PAGE_SIZE);
  const hasActiveFilters = ["property_id", "task_type", "scheduled_from", "scheduled_to", "assigned_to"].some(
    (key) => searchParams.has(key),
  );

  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [panelOpened, setPanelOpened] = useState(false);
  const [filterDrawerOpened, setFilterDrawerOpened] = useState(false);

  const handleTaskClick = (taskId: string) => {
    setSelectedTaskId(taskId);
    setPanelOpened(true);
  };

  const handlePageChange = (newPage: number) => {
    const params = new URLSearchParams(searchParams);
    params.set("page", String(newPage - 1));
    navigate(`/tasks?${params.toString()}`);
  };

  if (error) {
    return <LoaderError title={t("tasks.title")} />;
  }

  return (
    <Container fluid={true}>
      <ContentHeader
        title={`${t("tasks.title")} (${total})`}
        subtitle={t("tasks.subtitle")}
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
                onClick={() => navigate("/tasks/new")}
                visibleFrom="sm"
              >
                {t("tasks.create")}
              </MainButton>
              <ActionIcon
                variant="filled"
                color="dark"
                size="lg"
                radius="xl"
                onClick={() => navigate("/tasks/new")}
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
          title={t("tasks.emptyNoPropertiesTitle")}
          subtitle={t("tasks.emptyNoPropertiesSubtitle")}
          image="/icons/clipboard.svg"
          button={{ name: t("common.addFirstProperty"), url: "/properties/new" }}
        />
      ) : tasks.length === 0 && !hasActiveFilters ? (
        <EmptyState
          title={t("tasks.emptyTitle")}
          subtitle={t("tasks.emptySubtitle")}
          image="/icons/clipboard.svg"
        />
      ) : (
        <div className="flex gap-6 items-start">
          <div className="flex-1 min-w-0">
            {/* Mobile card view */}
            <div className="block md:hidden space-y-3">
              {tasks.length === 0 && (
                <p className="py-12 text-center text-sm text-slate-400">
                  {t("tasks.noResultsFiltered")}
                </p>
              )}
              {tasks.map((task: Task) => (
                <div
                  key={task.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => handleTaskClick(task.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      handleTaskClick(task.id);
                    }
                  }}
                  className="bg-white rounded-xl p-4 shadow-sm border border-slate-100 cursor-pointer active:bg-slate-50"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2">
                      {isToday(task.from) && (
                        <span className="inline-block w-2 h-2 rounded-full bg-green-500 shrink-0" />
                      )}
                      <p className="text-base font-bold text-slate-900">
                        {task.property.name || t("tasks.unknownProperty")}
                      </p>
                      <span
                        className={`inline-block mt-1 px-2 py-0.5 rounded border text-xs font-medium ${typeBadgeClasses(task.task_type)}`}
                      >
                        {getTaskTypeLabel(task.task_type, t)}
                      </span>
                    </div>
                  </div>
                  <div className="mt-3 space-y-1.5">
                    <div className="flex items-center gap-2 text-sm text-slate-600">
                      <IconCalendar size={16} className="text-slate-400" />
                      <span>
                        {formatDate(task.from)} ·{" "}
                        {formatTime(task.from)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-slate-600">
                      <IconUser size={16} className="text-slate-400" />
                      <span>{task.assigned_to_name}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Desktop table view */}
            <div className="hidden md:block bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-slate-50 border-b border-slate-200">
                      <th className="w-4 pl-3 pr-0 py-4"></th>
                      <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                        {t("tasks.col.property")}
                      </th>
                      <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                        {t("tasks.col.type")}
                      </th>
                      <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                        {t("tasks.col.dateTime")}
                      </th>
                      <th className="px-2.5 py-4 text-xs font-bold text-slate-500 uppercase tracking-wider">
                        {t("tasks.col.assignedTo")}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {tasks.length === 0 && (
                      <tr>
                        <td colSpan={5} className="px-6 py-12 text-center text-sm text-slate-400">
                          {t("tasks.noResultsFiltered")}
                        </td>
                      </tr>
                    )}
                    {tasks.map((task: Task) => (
                      <tr
                        key={task.id}
                        role="button"
                        tabIndex={0}
                        onClick={() => handleTaskClick(task.id)}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            handleTaskClick(task.id);
                          }
                        }}
                        className="group hover:bg-slate-50 transition-colors cursor-pointer"
                      >
                        <td className="w-4 pl-3 pr-0 py-4">
                          {isToday(task.from) && (
                            <span className="inline-block w-2 h-2 rounded-full bg-green-500" />
                          )}
                        </td>
                        <td className="px-2.5 py-4">
                          <p className="font-semibold text-xs text-slate-900">
                            {task.property.name || t("tasks.unknownProperty")}
                          </p>
                        </td>
                        <td className="px-2.5 py-4">
                          <span
                            className={`inline-block px-2 py-0.5 rounded border text-xs font-medium ${typeBadgeClasses(task.task_type)}`}
                          >
                            {getTaskTypeLabel(task.task_type, t)}
                          </span>
                        </td>
                        <td className="px-2.5 py-4">
                          <div className="flex flex-col">
                            <span className="font-medium text-xs text-slate-900">
                              {formatDate(task.from)}
                            </span>
                            <span className="text-xs text-slate-500">
                              {formatTime(task.from)}
                            </span>
                          </div>
                        </td>
                        <td className="px-2.5 py-4">
                          <div className="flex items-center gap-1.5 text-xs text-slate-700">
                            <IconUser size={14} className="text-slate-400" />
                            <span>{task.assigned_to_name}</span>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
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
            <TasksFilterPanel properties={properties} />
          </Box>
        </div>
      )}

      <TaskDetailsPanel
        taskId={selectedTaskId}
        opened={panelOpened}
        onClose={() => {
          setPanelOpened(false);
          setSelectedTaskId(null);
          revalidate();
        }}
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
          <TasksFilterPanel properties={properties} inDrawer />
        </Box>
      </Drawer>
    </Container>
  );
}
