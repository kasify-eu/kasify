import { Container, Divider, SegmentedControl, Select } from "@mantine/core";
import { IconCalendarMonth, IconTimeline } from "@tabler/icons-react";
import { useMemo, useState } from "react";
import {
  useLoaderData,
  useNavigate,
  useRevalidator,
  useSearchParams,
} from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/calendar";
import { ContentHeader } from "~/components/content-header";
import { EmptyState } from "~/components/empty-state/empty-state";
import { BookingDetailsPanel } from "~/components/booking-details-panel";
import { TaskDetailsPanel } from "~/components/task-details-panel";
import { TaskCreateDrawer } from "~/components/task-create-drawer";
import { CalendarTimeline } from "~/components/calendar-timeline";
import { CalendarMonth } from "~/components/calendar-month";
import { CalendarToolbar } from "~/components/calendar-toolbar";
import { ApiClient, createApiConfig } from "~/lib/api-client";
import type { PropertyCalendar } from "~/types/calendar";
import { propertyToRow } from "~/components/calendar-shared/event-mapping";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Calendar" }];
}

type ViewMode = "timeline" | "month";

function todayDate(): Date {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function parseMonthParam(value: string | null): Date | null {
  if (!value) return null;
  const m = /^(\d{4})-(\d{2})$/.exec(value);
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]);
  if (month < 1 || month > 12) return null;
  return new Date(year, month - 1, 1);
}

function monthParam(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function daysInMonth(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
}

function addDays(d: Date, n: number): Date {
  const x = new Date(d);
  x.setDate(x.getDate() + n);
  return x;
}

function addMonths(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth() + n, 1);
}

/**
 * Visible window for the month grid: Monday of the week containing the 1st
 * through Sunday of the week containing the last day. Used as the loader's
 * fetch range so both the timeline and month-grid views have what they need.
 */
function visibleWindow(monthStart: Date): { from: Date; to: Date } {
  const first = monthStart;
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0);
  const startOffset = (first.getDay() + 6) % 7;
  const start = addDays(first, -startOffset);
  const totalCells = Math.ceil((startOffset + last.getDate()) / 7) * 7;
  const end = addDays(start, totalCells - 1);
  return { from: start, to: end };
}

export async function loader({ request }: Route.LoaderArgs) {
  const apiClient = await ApiClient.createAuthenticated(
    request,
    createApiConfig(),
  );
  const url = new URL(request.url);
  const today = todayDate();
  const monthStart =
    parseMonthParam(url.searchParams.get("month")) ??
    new Date(today.getFullYear(), today.getMonth(), 1);
  const { from, to } = visibleWindow(monthStart);
  const { data } = await apiClient.getCalendarEvents({
    from: isoDate(from),
    to: isoDate(to),
  });
  return { response: data, monthIso: isoDate(monthStart) };
}

function parseView(value: string | null): ViewMode | null {
  return value === "timeline" || value === "month" ? value : null;
}

export default function CalendarPage() {
  const { t } = useTranslation();
  const { response, monthIso } = useLoaderData<typeof loader>();
  const revalidator = useRevalidator();
  const navigate = useNavigate();

  const hasProperties = response.properties.length > 0;

  const [selectedBookingId, setSelectedBookingId] = useState<string | null>(null);
  const [bookingPanelOpen, setBookingPanelOpen] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [taskPanelOpen, setTaskPanelOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [createPropertyId, setCreatePropertyId] = useState<string | null>(null);
  const [createDate, setCreateDate] = useState<Date | null>(null);

  const handleCreate = (propertyId: string, date: Date) => {
    setCreatePropertyId(propertyId);
    setCreateDate(date);
    setCreateOpen(true);
  };

  const fallbackGuestLabel = t("calendar.guestFallback");

  const rows = useMemo(
    () =>
      response.properties.map((p: PropertyCalendar) =>
        propertyToRow(p, fallbackGuestLabel),
      ),
    [response, fallbackGuestLabel],
  );

  const [y, m] = monthIso.split("-").map(Number);
  const monthStart = new Date(y, m - 1, 1);
  const today = todayDate();
  const currentMonthStart = new Date(today.getFullYear(), today.getMonth(), 1);

  const [searchParams] = useSearchParams();
  const urlView = parseView(searchParams.get("view"));
  const urlPropertyId = searchParams.get("property");

  const defaultView: ViewMode = rows.length > 1 ? "timeline" : "month";
  const view: ViewMode = urlView ?? defaultView;

  const selectedRow =
    (urlPropertyId && rows.find((r) => r.id === urlPropertyId)) ||
    rows[0] ||
    null;

  const buildSearch = (next: {
    month?: string | null;
    view?: ViewMode | null;
    property?: string | null;
  }): string => {
    const params = new URLSearchParams(searchParams);
    const apply = (key: string, value: string | null | undefined) => {
      if (value === undefined) return;
      if (value === null || value === "") params.delete(key);
      else params.set(key, value);
    };
    apply("month", next.month);
    apply("view", next.view);
    apply("property", next.property);
    const s = params.toString();
    return s ? `?${s}` : ".";
  };

  const goToMonth = (next: Date) => {
    const isCurrent = monthParam(next) === monthParam(currentMonthStart);
    navigate(
      buildSearch({ month: isCurrent ? null : monthParam(next) }),
    );
  };

  const setView = (next: ViewMode) => {
    navigate(
      buildSearch({
        view: next === defaultView ? null : next,
        // Drop the property param when leaving month view; it's irrelevant.
        property: next === "month" ? undefined : null,
      }),
    );
  };

  const setPropertyId = (id: string) => {
    navigate(buildSearch({ property: id }));
  };

  const rangeDays = daysInMonth(monthStart);

  const propertyOptions = useMemo(
    () => rows.map((r) => ({ value: r.id, label: r.name })),
    [rows],
  );

  const showPropertySelect = view === "month" && rows.length > 1;

  const viewPicker = (
    <SegmentedControl
      size="xs"
      value={view}
      color="black"
      onChange={(v) => setView(v as ViewMode)}
      data={[
        {
          value: "timeline",
          label: (
            <span
              role="img"
              aria-label={t("calendar.viewTimeline")}
              title={t("calendar.viewTimeline")}
              style={{ display: "flex", alignItems: "center" }}
            >
              <IconTimeline size={18} />
            </span>
          ),
        },
        {
          value: "month",
          label: (
            <span
              role="img"
              aria-label={t("calendar.viewMonth")}
              title={t("calendar.viewMonth")}
              style={{ display: "flex", alignItems: "center" }}
            >
              <IconCalendarMonth size={18} />
            </span>
          ),
        },
      ]}
      styles={{
        root: {
          background: "#fff",
          border: "1px solid var(--mantine-color-gray-3)",
        },
      }}
    />
  );

  if (!hasProperties) {
    return (
      <Container fluid={true}>
        <ContentHeader
          title={t("calendar.title")}
          subtitle={t("calendar.subtitle")}
        />
        <EmptyState
          title={t("calendar.emptyNoPropertiesTitle")}
          subtitle={t("calendar.emptyNoPropertiesSubtitle")}
          image="/icons/calendar.svg"
          button={{ name: t("common.addFirstProperty"), url: "/properties/new" }}
        />
      </Container>
    );
  }

  return (
    <Container fluid={true}>
      <ContentHeader
        title={t("calendar.title")}
        subtitle={t("calendar.subtitle")}
        marginBottom={12}
      />
      <CalendarToolbar
        month={monthStart}
        onPrev={() => goToMonth(addMonths(monthStart, -1))}
        onNext={() => goToMonth(addMonths(monthStart, 1))}
        left={
          <>
            {viewPicker}
            {showPropertySelect && selectedRow && (
              <>
                <Divider orientation="vertical" />
                <Select
                  size="xs"
                  data={propertyOptions}
                  value={selectedRow.id}
                  onChange={(v) => v && setPropertyId(v)}
                  allowDeselect={false}
                  checkIconPosition="right"
                  style={{ flex: "1 1 auto", minWidth: 0, maxWidth: 240 }}
                  styles={{
                    input: {
                      minHeight: 32,
                      height: 32,
                      paddingTop: 0,
                      paddingBottom: 0,
                      fontSize: 12,
                    },
                  }}
                />
              </>
            )}
          </>
        }
      />
      {view === "timeline" ? (
        <CalendarTimeline
          rows={rows}
          rangeStart={monthStart}
          rangeDays={rangeDays}
          onPick={(picked) => {
            if (picked.type === "booking") {
              setSelectedBookingId(picked.booking.id);
              setBookingPanelOpen(true);
            } else {
              setSelectedTaskId(picked.task.id);
              setTaskPanelOpen(true);
            }
          }}
          onCreate={handleCreate}
        />
      ) : (
        <CalendarMonth
          row={selectedRow}
          monthStart={monthStart}
          onPick={(picked) => {
            if (picked.type === "booking") {
              setSelectedBookingId(picked.booking.id);
              setBookingPanelOpen(true);
            } else {
              setSelectedTaskId(picked.task.id);
              setTaskPanelOpen(true);
            }
          }}
          onCreate={handleCreate}
        />
      )}

      <BookingDetailsPanel
        bookingId={selectedBookingId}
        opened={bookingPanelOpen}
        onClose={() => {
          setBookingPanelOpen(false);
          setSelectedBookingId(null);
        }}
        onUpdate={() => revalidator.revalidate()}
      />

      <TaskDetailsPanel
        taskId={selectedTaskId}
        opened={taskPanelOpen}
        onClose={() => {
          setTaskPanelOpen(false);
          setSelectedTaskId(null);
        }}
        onUpdate={() => revalidator.revalidate()}
      />

      <TaskCreateDrawer
        opened={createOpen}
        onClose={() => setCreateOpen(false)}
        properties={rows.map((r) => ({ id: r.id, name: r.name }))}
        initialPropertyId={createPropertyId}
        initialDate={createDate}
        onCreated={() => revalidator.revalidate()}
      />
    </Container>
  );
}
