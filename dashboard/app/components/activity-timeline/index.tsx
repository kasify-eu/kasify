import {
  IconBabyCarriage,
  IconCheck,
  IconHome,
  IconLogin,
  IconLogout,
  IconMoodKid,
  IconMoon,
  IconSparkles,
  IconTool,
  IconUser,
} from "@tabler/icons-react";
import { Box, Group, Stack, Text } from "@mantine/core";
import { useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import type { Activity, TimelineGroup } from "~/types/activities";
import type { Booking } from "~/types/bookings";
import { EmptyState } from "~/components/empty-state/empty-state";
import {
  computeSesState,
  SES_STATE_LABEL_KEY,
  type SesState,
} from "~/lib/ses-status";
import classes from "./index.module.css";

type ActivityKind = Activity["type"];

interface ActivityTimelineProps {
  timeline: TimelineGroup[];
  onBookingClick?: (bookingId: string) => void;
  onTaskClick?: (taskId: string) => void;
}

export function ActivityTimeline({
  timeline,
  onBookingClick,
  onTaskClick,
}: ActivityTimelineProps) {
  const { t, i18n } = useTranslation();

  if (!timeline || timeline.length === 0) {
    return (
      <EmptyState
        title="No upcoming activities"
        image="/icons/calendar.svg"
        subtitle="Scheduled jobs, check-ins, and check-outs will appear here automatically"
      />
    );
  }

  function dateOffsetLabel(offset: number): string {
    if (offset === 0) return t("timeline.today");
    if (offset === 1) return t("timeline.tomorrow");
    if (offset === -1) return t("timeline.yesterday");
    if (offset > 1) return t("timeline.inDays", { count: offset });
    return t("timeline.daysAgo", { count: -offset });
  }

  return (
    <Box className={classes.root}>
      {timeline.map((group) => {
        const propertyGroups = groupByProperty(group.activities);
        return (
          <Box key={group.date}>
            <Group className={classes.day} gap="md" wrap="nowrap" align="baseline">
              <Group gap={6} align="baseline" wrap="nowrap">
                <Text component="span" className={classes.dayRel}>
                  {dateOffsetLabel(group.date_offset)}
                </Text>
                <Text component="span" className={classes.dayDate}>
                  <span className={classes.dayDateWkd}>
                    {new Date(group.date + "T00:00:00").toLocaleDateString(
                      i18n.language,
                      { weekday: "long" },
                    )}
                  </span>
                  {", "}
                  {new Date(group.date + "T00:00:00").toLocaleDateString(
                    i18n.language,
                    { day: "numeric", month: "long" },
                  )}
                </Text>
              </Group>
              <Box className={classes.daySpacer} />
            </Group>

            <Stack gap={10} className={classes.sectionBody}>
              {propertyGroups.map((pg) => (
                <PropertyBlock
                  key={pg.propertyId}
                  propertyName={pg.propertyName}
                  activities={pg.activities}
                  onBookingClick={onBookingClick}
                  onTaskClick={onTaskClick}
                />
              ))}
            </Stack>
          </Box>
        );
      })}
    </Box>
  );
}

function groupByProperty(activities: Activity[]): {
  propertyId: string;
  propertyName: string;
  activities: Activity[];
}[] {
  // Tiebreaker order when two activities share the same time.
  const order: ActivityKind[] = [
    "current_guest",
    "check_out",
    "cleaning",
    "maintenance",
    "check_in",
    "booking",
  ];
  const sorted = [...activities].sort((a, b) => {
    // current_guest has no real time (renders as "now"); keep it pinned on top.
    const aOngoing = a.type === "current_guest";
    const bOngoing = b.type === "current_guest";
    if (aOngoing !== bOngoing) return aOngoing ? -1 : 1;

    const byTime = (a.time ?? "").localeCompare(b.time ?? "");
    if (byTime !== 0) return byTime;
    return order.indexOf(a.type) - order.indexOf(b.type);
  });

  const byProp = new Map<
    string,
    { propertyId: string; propertyName: string; activities: Activity[] }
  >();
  for (const a of sorted) {
    const name =
      a.booking?.property?.name ??
      a.task?.property_name ??
      "";
    const existing = byProp.get(a.property_id);
    if (existing) {
      existing.activities.push(a);
    } else {
      byProp.set(a.property_id, {
        propertyId: a.property_id,
        propertyName: name,
        activities: [a],
      });
    }
  }
  return [...byProp.values()];
}

interface PropertyBlockProps {
  propertyName: string;
  activities: Activity[];
  onBookingClick?: (bookingId: string) => void;
  onTaskClick?: (taskId: string) => void;
}

function PropertyBlock({
  propertyName,
  activities,
  onBookingClick,
  onTaskClick,
}: PropertyBlockProps) {
  return (
    <Box className={classes.pblock}>
      <Box className={classes.pblockHead}>
        <Text component="span" className={classes.pblockHeadName}>
          {propertyName}
        </Text>
      </Box>
      <Box className={classes.pblockBody}>
        {activities.map((activity) => (
          <ActivityRow
            key={activity.id}
            activity={activity}
            onBookingClick={onBookingClick}
            onTaskClick={onTaskClick}
          />
        ))}
      </Box>
    </Box>
  );
}

interface ActivityRowProps {
  activity: Activity;
  onBookingClick?: (bookingId: string) => void;
  onTaskClick?: (taskId: string) => void;
}

function ActivityRow({
  activity,
  onBookingClick,
  onTaskClick,
}: ActivityRowProps) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const isTask =
    activity.type === "cleaning" || activity.type === "maintenance";

  const handleClick = () => {
    if (isTask && activity.task) {
      if (onTaskClick) onTaskClick(activity.task.id);
      else navigate(`/tasks/${activity.task.id}`);
    } else if (onBookingClick && activity.booking) {
      onBookingClick(activity.booking.id);
    }
  };

  return (
    <Box
      className={classes.row}
      data-kind={activity.type}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          handleClick();
        }
      }}
    >
      <TimeColumn activity={activity} lang={i18n.language} t={t} />
      <Box className={classes.disc} data-kind={activity.type}>
        <KindIcon kind={activity.type} />
      </Box>
      <Box className={classes.body}>
        <RowTitle activity={activity} t={t} />
        <RowMeta activity={activity} t={t} lang={i18n.language} />
        {activity.type === "check_in" && activity.booking?.special_requests && (
          <Box className={classes.special}>
            {activity.booking.special_requests}
          </Box>
        )}
        {isTask && activity.task?.notes && (
          <Box className={classes.notes}>{activity.task.notes}</Box>
        )}
      </Box>
    </Box>
  );
}

function KindIcon({ kind }: { kind: ActivityKind }) {
  const size = 18;
  const stroke = 2.25;
  switch (kind) {
    case "check_in":
      return <IconLogin size={size} stroke={stroke} />;
    case "check_out":
      return <IconLogout size={size} stroke={stroke} />;
    case "current_guest":
      return <IconMoon size={size} stroke={stroke} />;
    case "cleaning":
      return <IconSparkles size={size} stroke={stroke} />;
    case "maintenance":
      return <IconTool size={size} stroke={stroke} />;
    default:
      return <IconHome size={size} stroke={stroke} />;
  }
}

function TimeColumn({
  activity,
  lang,
  t,
}: {
  activity: Activity;
  lang: string;
  t: ReturnType<typeof useTranslation>["t"];
}) {
  if (activity.type === "current_guest") {
    const sub = computeDayOfStay(activity, t);
    return (
      <Box className={classes.time}>
        <span className={`${classes.timeMain} ${classes.timeMainQuiet}`}>
          {t("timeline.now")}
        </span>
        {sub && <span className={classes.timeSub}>{sub}</span>}
      </Box>
    );
  }

  if (activity.type === "cleaning" || activity.type === "maintenance") {
    return (
      <Box className={classes.time}>
        <span className={classes.timeMain}>{activity.time ?? "—"}</span>
        {activity.end_time && (
          <span className={classes.timeSub}>– {activity.end_time}</span>
        )}
      </Box>
    );
  }

  // check_in / check_out / booking
  const subKey =
    activity.type === "check_in"
      ? "timeline.checkIn"
      : activity.type === "check_out"
        ? "timeline.checkOut"
        : null;
  return (
    <Box className={classes.time}>
      <span className={classes.timeMain}>{activity.time ?? "—"}</span>
      {subKey && <span className={classes.timeSub}>{t(subKey)}</span>}
    </Box>
  );
}

function computeDayOfStay(
  activity: Activity,
  t: ReturnType<typeof useTranslation>["t"],
): string | null {
  const b = activity.booking;
  if (!b) return null;
  const fromDate = parseIsoDate(b.from);
  const toDate = parseIsoDate(b.to);
  if (!fromDate || !toDate) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const msPerDay = 86_400_000;
  const total = Math.max(1, Math.round((toDate.getTime() - fromDate.getTime()) / msPerDay));
  const day = Math.max(
    1,
    Math.min(total, Math.round((today.getTime() - fromDate.getTime()) / msPerDay) + 1),
  );
  return t("timeline.dayOfTotal", { day, total });
}

function parseIsoDate(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return null;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
}

function RowTitle({
  activity,
  t,
}: {
  activity: Activity;
  t: ReturnType<typeof useTranslation>["t"];
}) {
  if (activity.type === "cleaning" || activity.type === "maintenance") {
    return (
      <span className={classes.title}>
        {t(
          activity.type === "cleaning"
            ? "timeline.cleaning"
            : "timeline.maintenance",
        )}
      </span>
    );
  }

  const b = activity.booking;
  const guestName = `${b?.guest_first_name ?? ""} ${b?.guest_last_name ?? ""}`.trim();
  return <span className={classes.title}>{guestName || "—"}</span>;
}

function RowMeta({
  activity,
  t,
  lang,
}: {
  activity: Activity;
  t: ReturnType<typeof useTranslation>["t"];
  lang: string;
}) {
  if (activity.type === "cleaning" || activity.type === "maintenance") {
    const assignee = activity.task?.assigned_to_name;
    return (
      <Box className={classes.meta}>
        <span className={classes.mk}>
          <IconUser size={12} />
          <span className={classes.mkVal}>
            {assignee ?? t("timeline.unassigned")}
          </span>
        </span>
      </Box>
    );
  }

  const b = activity.booking;
  if (!b) return null;
  const adults = b.adults_count ?? 0;
  const children = b.children_count ?? 0;
  const infants = b.infants_count ?? 0;
  const items: React.ReactNode[] = [];

  const guestParts: React.ReactNode[] = [];
  if (adults > 0) {
    guestParts.push(
      <span key="a" className={classes.guestCount} title={t("timeline.adults", { count: adults })}>
        <IconUser size={12} />
        {adults}
      </span>,
    );
  }
  if (children > 0) {
    guestParts.push(
      <span key="c" className={classes.guestCount} title={t("timeline.children", { count: children })}>
        <IconMoodKid size={12} />
        {children}
      </span>,
    );
  }
  if (infants > 0) {
    guestParts.push(
      <span key="i" className={classes.guestCount} title={t("timeline.babies", { count: infants })}>
        <IconBabyCarriage size={12} />
        {infants}
      </span>,
    );
  }
  if (guestParts.length > 0) {
    items.push(
      <span key="guests" className={classes.guests}>
        {guestParts}
      </span>,
    );
  }

  if (b.channel) {
    items.push(<ChannelChip key="channel" channel={b.channel} t={t} />);
  }

  if (activity.type === "current_guest") {
    items.push(
      <span key="leaves" className={classes.mk}>
        <span className={classes.mkVal}>
          {t("timeline.checkoutOn", { date: formatShortDate(b.to, lang) })}
        </span>
      </span>,
    );
  }

  if (activity.type === "check_in" || activity.type === "current_guest") {
    items.push(<CheckinStatus key="checkin-status" booking={b} t={t} />);
    const sesState = computeSesState(b);
    if (sesState) {
      items.push(<SesStatus key="ses-status" state={sesState} t={t} />);
    }
  }

  return <Box className={classes.meta}>{withSeparators(items)}</Box>;
}

function withSeparators(nodes: React.ReactNode[]): React.ReactNode[] {
  const out: React.ReactNode[] = [];
  nodes.forEach((n, i) => {
    if (i > 0) out.push(<span key={`sep-${i}`} className={classes.mkSep} />);
    out.push(n);
  });
  return out;
}

function ChannelChip({
  channel,
  t,
}: {
  channel: string;
  t: ReturnType<typeof useTranslation>["t"];
}) {
  const brand = channel.toLowerCase();
  const knownBrands = ["airbnb", "booking", "direct", "vrbo", "google"];
  const brandKey = knownBrands.includes(brand) ? brand : "other";
  const labelKey =
    brand === "airbnb"
      ? "timeline.channelAirbnb"
      : brand === "booking"
        ? "timeline.channelBooking"
        : brand === "direct"
          ? "timeline.channelDirect"
          : brand === "vrbo"
            ? "timeline.channelVrbo"
            : brand === "google"
              ? "timeline.channelGoogle"
              : null;
  return (
    <span className={classes.channel}>
      <span className={classes.channelDot} data-brand={brandKey} />
      {labelKey ? t(labelKey) : channel}
    </span>
  );
}

function CheckinStatus({
  booking,
  t,
}: {
  booking: Booking;
  t: ReturnType<typeof useTranslation>["t"];
}) {
  const completed = !!booking.checkin_completed_at;
  return (
    <span className={classes.mk}>
      <span
        className={classes.statusDot}
        data-state={completed ? "completed" : "pending"}
      />
      <span className={classes.mkVal}>
        {t(
          completed ? "timeline.checkinCompleted" : "timeline.checkinPending",
        )}
      </span>
    </span>
  );
}

function SesStatus({
  state,
  t,
}: {
  state: SesState;
  t: ReturnType<typeof useTranslation>["t"];
}) {
  const labelKey = SES_STATE_LABEL_KEY[state];
  return (
    <span className={classes.mk}>
      <span className={classes.statusDot} data-state={state} />
      <span className={classes.mkVal}>{t(labelKey)}</span>
    </span>
  );
}

function formatShortDate(iso: string | null | undefined, lang: string): string {
  const d = parseIsoDate(iso);
  if (!d) return "—";
  return d.toLocaleDateString(lang, { day: "numeric", month: "short" });
}
