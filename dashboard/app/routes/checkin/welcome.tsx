import {
  Box,
  Button,
  Card,
  Divider,
  Group,
  Image,
  Paper,
  Progress,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import {
  IconArrowRight,
  IconCalendar,
  IconCircleCheckFilled,
  IconGavel,
  IconLock,
  IconRosetteDiscountCheckFilled,
  IconShare,
  IconStack2,
} from "@tabler/icons-react";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { useTranslation } from "react-i18next";
import { useLoaderData, useNavigate } from "react-router";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import type { Route } from "./+types/welcome";

export function meta({ data }: Route.MetaArgs) {
  const propertyName = data?.checkin?.property?.name ?? "Guest Check-in";
  return [{ title: `Check-in | ${propertyName}` }];
}

export async function loader({ params, request }: Route.LoaderArgs) {
  const bookingCode = params.bookingCode;

  if (!bookingCode) {
    return { checkin: null, error: "notFound" as const };
  }

  try {
    const client = ApiClient.createPublic(request, createApiConfig());
    const response = await client.getCheckin(bookingCode);
    return { checkin: response.data, error: null };
  } catch (err) {
    if (err instanceof ApiClientError && err.status === 404) {
      return { checkin: null, error: "notFound" as const };
    }
    if (err instanceof ApiClientError && err.status === 429) {
      return { checkin: null, error: "rateLimited" as const };
    }
    return { checkin: null, error: "loadFailed" as const };
  }
}

function formatDateRange(from: string, to: string, locale: string): string {
  const fromDate = new Date(from + "T00:00:00");
  const toDate = new Date(to + "T00:00:00");
  const currentYear = new Date().getFullYear();
  const showYear =
    fromDate.getFullYear() !== currentYear ||
    toDate.getFullYear() !== currentYear;
  const opts: Intl.DateTimeFormatOptions = {
    month: "long",
    day: "numeric",
    ...(showYear ? { year: "numeric" as const } : {}),
  };
  // Keep each date unbreakable (spaces -> NBSP) and glue the dash to the
  // first date, so the only place a line can wrap is between "—" and the
  // second date.
  const nbsp = (s: string) => s.replace(/ /g, " ");
  const fromStr = nbsp(fromDate.toLocaleDateString(locale, opts));
  const toStr = nbsp(toDate.toLocaleDateString(locale, opts));
  return `${fromStr} — ${toStr}`;
}

function getNights(from: string, to: string): number {
  const diff =
    new Date(to + "T00:00:00").getTime() -
    new Date(from + "T00:00:00").getTime();
  return Math.round(diff / (1000 * 60 * 60 * 24));
}

export default function CheckinWelcomePage() {
  const { checkin, error } = useLoaderData<typeof loader>();
  const { t, i18n } = useTranslation();
  const locale = i18n.language;
  const navigate = useNavigate();

  if (error || !checkin) {
    const titleKey =
      error === "rateLimited" ? "checkin.rateLimited" : "checkin.notFound";
    const messageKey =
      error === "rateLimited"
        ? "checkin.rateLimitedMessage"
        : "checkin.notFoundMessage";
    return (
      <Stack align="center" gap="lg" py={80} px="md">
        <Title order={3}>{t(titleKey)}</Title>
        <Text size="sm" c="dimmed" ta="center">
          {t(messageKey)}
        </Text>
      </Stack>
    );
  }

  const nights = getNights(checkin.from, checkin.to);
  const registeredCount = checkin.guests.length;
  const totalGuests = checkin.guests_number;

  return (
    <>
        {/* Hero Image */}
        <Box pos="relative">
          <Image
            src={checkin.property.cover_image_url || "/imgs/checkin-hero.svg"}
            h={{ base: 180, sm: 260 }}
            fit="cover"
            w="100%"
            fallbackSrc="/imgs/checkin-hero.svg"
          />
          <Box
            pos="absolute"
            top={16}
            left={16}
            bg="rgba(255,255,255,0.85)"
            px="sm"
            py={8}
            maw="calc(100% - 32px)"
            style={{ borderRadius: 8, backdropFilter: "blur(8px)" }}
          >
            <Group gap={8} wrap="nowrap">
              <Box
                style={{
                  width: 28,
                  height: 28,
                  flexShrink: 0,
                  backgroundColor: "#0F172A",
                  borderRadius: 6,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <IconStack2 size={16} color="white" />
              </Box>
              <Text
                fw={700}
                size="sm"
                truncate
                style={{ letterSpacing: "-0.02em" }}
              >
                {t("checkin.welcome", { property: checkin.property.name })}
              </Text>
            </Group>
          </Box>
        </Box>

        {/* Reservation Details Card */}
        <Card
          radius="lg"
          shadow="sm"
          mx="md"
          mt={-32}
          pos="relative"
          style={{ zIndex: 1 }}
          p={{ base: "lg", sm: "xl" }}
        >
          <Stack gap="lg">
            {/* Dates + Booking Code */}
            <Group justify="space-between" wrap="wrap" gap="md">
              <Stack gap={4}>
                <Text
                  size="xs"
                  fw={700}
                  c="dimmed"
                  tt="uppercase"
                  style={{ letterSpacing: "0.05em" }}
                >
                  {t("checkin.dates")}
                </Text>
                <Group gap="xs" wrap="nowrap" align="flex-start">
                  <IconCalendar
                    size={18}
                    color="var(--mantine-color-blue-6)"
                    style={{ flexShrink: 0, marginTop: 3 }}
                  />
                  <Text
                    fw={700}
                    fz={{ base: "md", sm: "lg" }}
                  >
                    {formatDateRange(checkin.from, checkin.to, locale)}
                  </Text>
                </Group>
                <Text size="xs" c="dimmed">
                  {t("checkin.nights", { count: nights })}
                </Text>
              </Stack>
              <Text size="xs" c="dimmed">
                {t("checkin.reservationReference")}: {checkin.booking_code}
              </Text>
            </Group>

            {/* Completed state */}
            {checkin.checkin_completed && (
              <Paper
                p="lg"
                radius="md"
                mt="md"
                style={{
                  backgroundColor: "var(--mantine-color-green-0)",
                  border: "1px solid var(--mantine-color-green-2)",
                }}
              >
                <Stack align="center" gap="sm">
                  <Box
                    style={{
                      width: 56,
                      height: 56,
                      borderRadius: "50%",
                      backgroundColor: "white",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <IconRosetteDiscountCheckFilled
                      size={36}
                      color="var(--mantine-color-green-6)"
                    />
                  </Box>
                  <Title
                    order={4}
                    ta="center"
                    style={{ letterSpacing: "-0.02em" }}
                  >
                    {t("checkinGuest.completedTitle")}
                  </Title>
                  <Text size="sm" c="dimmed" ta="center" maw={320}>
                    {t("checkinGuest.completedBodyGeneric")}
                  </Text>
                </Stack>
              </Paper>
            )}

            {/* Closed state */}
            {checkin.checkin_closed && !checkin.checkin_completed && (
              <Paper
                p="lg"
                radius="md"
                mt="md"
                style={{
                  backgroundColor: "var(--mantine-color-gray-0)",
                  border: "1px solid var(--mantine-color-gray-3)",
                }}
              >
                <Stack align="center" gap="sm">
                  <Box
                    style={{
                      width: 56,
                      height: 56,
                      borderRadius: "50%",
                      backgroundColor: "white",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <IconLock
                      size={28}
                      color="var(--mantine-color-gray-6)"
                    />
                  </Box>
                  <Text size="sm" c="dimmed" ta="center" maw={320}>
                    {t("checkin.registrationStatusClosed")}
                  </Text>
                </Stack>
              </Paper>
            )}

            {/* Active state: progress + CTA */}
            {!checkin.checkin_closed && !checkin.checkin_completed && (
              <>
                <Divider />

                {/* Unified guest progress */}
                <Stack align="center" gap="sm">
                  {registeredCount > 0 ? (
                    <>
                      <Title
                        order={2}
                        style={{
                          letterSpacing: "-0.02em",
                        }}
                      >
                        {registeredCount} {t("checkin.progressOf")} {totalGuests}
                      </Title>
                      <Text
                        size="xs"
                        fw={700}
                        c="dimmed"
                        tt="uppercase"
                        ta="center"
                        style={{ letterSpacing: "0.1em" }}
                      >
                        {t("checkin.guestsRegistered")}
                      </Text>
                    </>
                  ) : (
                    <>
                      <Title
                        order={2}
                        style={{
                          letterSpacing: "-0.02em",
                        }}
                      >
                        {totalGuests}
                      </Title>
                      <Text
                        size="xs"
                        fw={700}
                        c="dimmed"
                        tt="uppercase"
                        ta="center"
                        style={{ letterSpacing: "0.1em" }}
                      >
                        {t("checkin.guestsAwaitingRegistration", { count: totalGuests })}
                      </Text>
                    </>
                  )}
                  {registeredCount > 0 && (
                    <Progress
                      value={(registeredCount / totalGuests) * 100}
                      size={4}
                      color="blue"
                      w="100%"
                      mt="xs"
                      radius="xl"
                    />
                  )}
                  <Text size="sm" c="dimmed" ta="center">
                    {t("checkin.welcomeSubtitle")}
                  </Text>
                </Stack>

                {registeredCount === 0 && <Divider />}

                {/* Registered guest list */}
                {registeredCount > 0 && (
                  <Stack gap={0}>
                    {checkin.guests.map((guest) => (
                      <Group
                        key={guest.id}
                        justify="space-between"
                        py="sm"
                        style={{
                          borderBottom: "1px solid var(--mantine-color-gray-2)",
                        }}
                      >
                        <Text fw={500}>
                          {guest.first_name} {guest.last_name}
                        </Text>
                        <IconCircleCheckFilled
                          size={20}
                          color="var(--mantine-color-green-6)"
                        />
                      </Group>
                    ))}
                  </Stack>
                )}

                <Button
                  fullWidth
                  size="lg"
                  color="dark"
                  mt="md"
                  rightSection={<IconArrowRight size={20} />}
                  onClick={() => navigate(`/checkin/${checkin.booking_code}/guest`)}
                  styles={{ label: { whiteSpace: "normal" } }}
                  style={{
                    fontWeight: 700,
                    fontSize: "1.1rem",
                    minHeight: 56,
                    height: "auto",
                    paddingTop: 12,
                    paddingBottom: 12,
                  }}
                >
                  {t(
                    registeredCount > 0
                      ? "checkin.continueCheckin"
                      : "checkin.startCheckin",
                  )}
                </Button>

                {totalGuests > 1 && (
                  <Button
                    fullWidth
                    size="sm"
                    variant="subtle"
                    color="blue"
                    leftSection={<IconShare size={18} />}
                    h="auto"
                    py={8}
                    styles={{ label: { whiteSpace: "normal" } }}
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(window.location.href);
                        showSuccessToast(t("checkinGuest.linkCopied"));
                      } catch {
                        showErrorToast(t("checkinGuest.linkCopyFailed"));
                      }
                    }}
                  >
                    {t("checkin.shareWithGuests")}
                  </Button>
                )}
              </>
            )}
          </Stack>
        </Card>

        {/* Legal Compliance Notice */}
        <Paper
          mx="md"
          mt="lg"
          p="md"
          radius="md"
          bg="gray.0"
          withBorder
          style={{ borderColor: "var(--mantine-color-gray-3)" }}
        >
          <Group gap="sm" align="flex-start" wrap="nowrap">
            <IconGavel
              size={20}
              color="var(--mantine-color-orange-7)"
              style={{ flexShrink: 0, marginTop: 2 }}
            />
            <Stack gap={4}>
              <Text size="sm" fw={700}>
                {t("checkin.legalTitle")}
              </Text>
              <Text size="xs" c="dimmed" lh={1.5}>
                {t(checkin.checkin_completed
                  ? "checkinGuest.complianceConfirmation"
                  : "checkin.legalNotice")}
              </Text>
            </Stack>
          </Group>
        </Paper>

    </>
  );
}
