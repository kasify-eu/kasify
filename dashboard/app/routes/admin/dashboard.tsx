import { Link, useLoaderData } from "react-router";
import { useTranslation } from "react-i18next";
import {
  Badge,
  Box,
  Group,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  ThemeIcon,
} from "@mantine/core";
import {
  IconAlertTriangle,
  IconArrowUp,
  IconBuildingEstate,
  IconCalendar,
  IconCalendarEvent,
  IconKey,
  IconLock,
  IconSend,
  IconUserCheck,
  IconUsers,
  IconWifi,
} from "@tabler/icons-react";
import { userContext } from "~/context";
import i18nextServer from "~/i18next.server";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import type { AdminOverviewResponse } from "~/types/admin-overview";
import type { Route } from "./+types/dashboard";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify Admin | Dashboard" }];
}

export async function loader({ request, context }: Route.LoaderArgs) {
  const user = context.get(userContext);
  const firstName = user?.given_name ?? user?.username ?? "";

  // Format the date here (server-side) so the client doesn't recompute it and
  // risk an SSR hydration mismatch from a differing browser timezone/locale.
  const locale = await i18nextServer.getLocale(request);
  const today = new Date().toLocaleDateString(locale, {
    weekday: "long",
    year: "numeric",
    month: "short",
    day: "numeric",
  });

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const response = await apiClient.getAdminOverview();
    return {
      overview: response.data,
      firstName,
      today,
      error: null as string | null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return {
      overview: null as AdminOverviewResponse | null,
      firstName,
      today,
      error: apiError.message,
    };
  }
}

// Each stat maps to a Mantine palette color (no hardcoded hex — see app/root.tsx theme).
type Tone = "blue" | "violet" | "teal" | "green" | "red";

const CARD_RADIUS = 20;

function StatIcon({
  tone,
  size = 50,
  children,
}: {
  tone: Tone;
  size?: number;
  children: React.ReactNode;
}) {
  return (
    <ThemeIcon variant="light" color={tone} radius="md" size={size}>
      {children}
    </ThemeIcon>
  );
}

function nf(n: number): string {
  return n.toLocaleString();
}

/** Big horizontal headline card: icon + number + label + "+N this month" delta. */
function HeadlineCard({
  tone,
  icon,
  value,
  label,
  delta,
  to,
}: {
  tone: Tone;
  icon: React.ReactNode;
  value: number;
  label: string;
  delta: string;
  to?: string;
}) {
  const card = (
    <Paper withBorder radius={CARD_RADIUS} p="20px" h="100%">
      <Group gap={16} wrap="nowrap">
        <StatIcon tone={tone}>{icon}</StatIcon>
        <Box style={{ minWidth: 0 }}>
          <Group gap={8} align="baseline" wrap="nowrap">
            <Text
              fw={800}
              style={{
                fontSize: 32,
                letterSpacing: "-0.04em",
                lineHeight: 1,
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {nf(value)}
            </Text>
            <Text c="dimmed" style={{ fontSize: 14.5 }}>
              {label}
            </Text>
          </Group>
          <Group gap={4} mt={9} c="green.7" style={{ fontSize: 12, fontWeight: 600 }}>
            <IconArrowUp size={12} stroke={2.6} />
            {delta}
          </Group>
        </Box>
      </Group>
    </Paper>
  );

  if (to) {
    return (
      <Link to={to} style={{ textDecoration: "none", color: "inherit", display: "block" }}>
        {card}
      </Link>
    );
  }
  return card;
}

/** Vertical stat card with an icon tile, big number, label, and a sub-note or status badge. */
function StatCard({
  tone,
  icon,
  value,
  label,
  note,
  badge,
}: {
  tone: Tone;
  icon: React.ReactNode;
  value: number;
  label: string;
  note?: string;
  badge?: React.ReactNode;
}) {
  return (
    <Paper withBorder radius={CARD_RADIUS} px={22} pt={22} pb={20}>
      <StatIcon tone={tone} size={46}>
        {icon}
      </StatIcon>
      <Group gap={10} mt={18} align="baseline" wrap="nowrap">
        <Text
          fw={800}
          style={{
            fontSize: 40,
            letterSpacing: "-0.04em",
            lineHeight: 1,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {nf(value)}
        </Text>
        <Text c="dimmed" style={{ fontSize: 15 }}>
          {label}
        </Text>
      </Group>
      {badge && <Box mt={14}>{badge}</Box>}
      {note && (
        <Text c="dimmed" mt={14} style={{ fontSize: 13 }}>
          {note}
        </Text>
      )}
    </Paper>
  );
}

function SectionHeading({ children }: { children: React.ReactNode }) {
  return (
    <Text
      mb={16}
      style={{ fontSize: 19, fontWeight: 700, letterSpacing: "-0.02em" }}
    >
      {children}
    </Text>
  );
}

export default function AdminDashboard() {
  const { overview, firstName, today, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();

  const monthDelta = (n: number) => t("adminDashboard.delta.newThisMonth", { count: n });

  return (
    <Box style={{ maxWidth: 1320, margin: "0 auto" }}>
      {/* header */}
      <Group justify="space-between" align="flex-start" mb={34} wrap="nowrap">
        <Box>
          <Text
            fw={800}
            style={{ fontSize: 33, letterSpacing: "-0.035em" }}
          >
            {firstName
              ? t("adminDashboard.greeting", { name: firstName })
              : t("adminDashboard.greetingNoName")}
          </Text>
          <Text c="dimmed" mt={8} style={{ fontSize: 16 }}>
            {t("adminDashboard.subtitle")}
          </Text>
        </Box>
        <Paper withBorder radius="md" px={15} py={10} style={{ flexShrink: 0 }}>
          <Group gap={9} wrap="nowrap" c="dimmed" style={{ fontSize: 14, whiteSpace: "nowrap" }}>
            <IconCalendar size={16} />
            {today}
          </Group>
        </Paper>
      </Group>

      {error || !overview ? (
        <Paper withBorder radius={CARD_RADIUS} p="lg">
          <Text c="dimmed">{t("adminDashboard.loadError")}</Text>
        </Paper>
      ) : (
        <Stack gap={34}>
          {/* headline stats */}
          <SimpleGrid cols={{ base: 1, sm: 2, xl: 4 }} spacing={16}>
            <HeadlineCard
              tone="violet"
              icon={<IconBuildingEstate size={24} stroke={1.9} />}
              value={overview.properties.total}
              label={t("adminDashboard.stats.properties")}
              delta={monthDelta(overview.properties.new_this_month)}
              to="/admin/properties"
            />
            <HeadlineCard
              tone="blue"
              icon={<IconUsers size={24} stroke={1.9} />}
              value={overview.users.total}
              label={t("adminDashboard.stats.users")}
              delta={monthDelta(overview.users.new_this_month)}
              to="/admin/users"
            />
            <HeadlineCard
              tone="teal"
              icon={<IconCalendarEvent size={24} stroke={1.9} />}
              value={overview.bookings.total}
              label={t("adminDashboard.stats.bookings")}
              delta={monthDelta(overview.bookings.new_this_month)}
              to="/admin/bookings"
            />
            <HeadlineCard
              tone="green"
              icon={<IconUserCheck size={24} stroke={1.9} />}
              value={overview.guests.total}
              label={t("adminDashboard.stats.guests")}
              delta={monthDelta(overview.guests.new_this_month)}
              to="/admin/guests"
            />
          </SimpleGrid>

          {/* SES.Hospedajes */}
          <Box>
            <SectionHeading>{t("adminDashboard.ses.title")}</SectionHeading>
            <SimpleGrid cols={{ base: 1, sm: 2 }} spacing={16}>
              <StatCard
                tone="blue"
                icon={<IconSend size={23} stroke={1.9} />}
                value={overview.ses.submissions_sent}
                label={t("adminDashboard.ses.sent")}
                note={t("adminDashboard.ses.sentNote")}
              />
              <StatCard
                tone="red"
                icon={<IconAlertTriangle size={23} stroke={1.9} />}
                value={overview.ses.submissions_with_errors}
                label={t("adminDashboard.ses.errors")}
                badge={
                  <Badge
                    color="red"
                    variant="light"
                    radius="sm"
                    tt="none"
                    leftSection={<IconAlertTriangle size={12} stroke={2.4} />}
                  >
                    {t("adminDashboard.ses.needsReview")}
                  </Badge>
                }
              />
            </SimpleGrid>
          </Box>

          {/* Integrations & booking codes */}
          <Box>
            <SectionHeading>
              {t("adminDashboard.integrations.title")}
            </SectionHeading>
            <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing={16}>
              <StatCard
                tone="violet"
                icon={<IconLock size={23} stroke={1.9} />}
                value={overview.integrations.nuki_integrations}
                label={t("adminDashboard.integrations.nuki")}
                note={t("adminDashboard.integrations.nukiNote")}
              />
              <StatCard
                tone="teal"
                icon={<IconWifi size={23} stroke={1.9} />}
                value={overview.integrations.unifi_integrations}
                label={t("adminDashboard.integrations.unifi")}
                note={t("adminDashboard.integrations.unifiNote")}
              />
              <StatCard
                tone="green"
                icon={<IconKey size={23} stroke={1.9} />}
                value={overview.integrations.booking_codes_created}
                label={t("adminDashboard.integrations.bookingCodes")}
                note={t("adminDashboard.integrations.bookingCodesNote")}
              />
            </SimpleGrid>
          </Box>
        </Stack>
      )}
    </Box>
  );
}
