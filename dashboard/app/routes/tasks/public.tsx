import { Anchor, Box, Card, Center, Container, Divider, Group, Stack, Text, Title } from "@mantine/core";
import { IconCalendar, IconClock, IconStack2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useFetcher, useLoaderData } from "react-router";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import type { TaskType } from "~/types/tasks";
import type { Route } from "./+types/public";

export async function loader({ request }: Route.LoaderArgs) {
  const url = new URL(request.url);
  const token = url.searchParams.get("token");

  if (!token) {
    return { task: null, error: "Missing task token" };
  }

  try {
    const client = ApiClient.createPublic(request, createApiConfig());
    const response = await client.getPublicTask(token);
    return { task: response.data, error: null, errorStatus: null };
  } catch (err) {
    if (err instanceof ApiClientError && err.status === 403) {
      return { task: null, error: "Task link expired", errorStatus: 403 };
    }
    if (err instanceof ApiClientError && err.status === 404) {
      return { task: null, error: "Task not found", errorStatus: 404 };
    }
    return { task: null, error: "Unable to load task", errorStatus: null };
  }
}

// API returns property-local datetimes labeled with `Z`. Strip the suffix so
// `new Date()` treats them as wall-clock time rather than UTC.
function parseLocal(iso: string): Date {
  return new Date(iso.replace(/[zZ]$/, "").replace(/([+-]\d{2}:?\d{2})$/, ""));
}

function formatTime(iso: string): string {
  const d = parseLocal(iso);
  if (isNaN(d.getTime())) return "";
  const hour = d.getHours();
  const minute = d.getMinutes().toString().padStart(2, "0");
  const suffix = hour >= 12 ? "PM" : "AM";
  const display = hour % 12 || 12;
  return `${display}:${minute} ${suffix}`;
}

function formatDate(iso: string, locale: string): string {
  return parseLocal(iso).toLocaleDateString(locale, {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}

function InlineLanguageSelector() {
  const { i18n } = useTranslation();
  const fetcher = useFetcher();

  const handleSelect = (value: string) => {
    i18n.changeLanguage(value);
    fetcher.submit({ locale: value }, { method: "post", action: "/api/set-locale" });
  };

  return (
    <Group gap="xs" justify="center">
      <Anchor
        size="xs"
        fw={i18n.language === "en" ? 700 : 400}
        c={i18n.language === "en" ? "dark" : "dimmed"}
        onClick={() => handleSelect("en")}
      >
        english
      </Anchor>
      <Anchor
        size="xs"
        fw={i18n.language === "es" ? 700 : 400}
        c={i18n.language === "es" ? "dark" : "dimmed"}
        onClick={() => handleSelect("es")}
      >
        español
      </Anchor>
    </Group>
  );
}

export default function PublicTaskPage() {
  const { task, error, errorStatus } = useLoaderData<typeof loader>();
  const { t, i18n } = useTranslation();

  const TASK_TYPE_LABELS: Record<TaskType, string> = {
    cleaning: t("tasks.type.cleaning"),
    maintenance: t("tasks.type.maintenance"),
    pool: t("tasks.type.pool"),
    gardening: t("tasks.type.gardening"),
  };

  return (
    <Container size="sm" py="xl">
      <Center>
        <Stack w="100%" gap="lg">
          <Stack gap="xs" align="center">
            <Group gap="sm">
              <Box
                style={{
                  width: 32,
                  height: 32,
                  backgroundColor: "#0F172A",
                  borderRadius: 8,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <IconStack2 size={20} color="white" />
              </Box>
              <Text fw={700} size="xl" style={{ letterSpacing: "-0.02em" }}>
                Kasify
              </Text>
            </Group>
            <InlineLanguageSelector />
          </Stack>

          <Card withBorder p="xl" radius="md">
            {error || !task ? (
              <Stack align="center" gap="md" py="xl">
                <img
                  src={errorStatus === 403 ? "/icons/task-done.svg" : "/icons/task-not-found.svg"}
                  alt=""
                  width={80}
                  height={80}
                />
                <Title order={3}>
                  {errorStatus === 403 ? t("publicTask.linkExpiredTitle") : t("publicTask.taskNotFound")}
                </Title>
              </Stack>
            ) : (
              <Stack gap="lg">
                <Stack gap="xs">
                  <Title order={2}>{task.task_type ? TASK_TYPE_LABELS[task.task_type] : ""}</Title>
                  <Text size="sm" c="dimmed" fw={600} tt="uppercase" mt="8">
                    {task.property.name}
                  </Text>
                  <Text size="sm" c="dimmed" pt="0" mt="0">
                    {task.property?.address}, {task.property?.city}
                  </Text>
                </Stack>

                <Divider />

                <Stack gap="sm">
                  <Text size="sm" fw={600} c="dimmed" tt="uppercase">
                    {t("publicTask.scheduledAt")}
                  </Text>
                  <Box style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <IconCalendar size={18} color="var(--mantine-color-dimmed)" />
                    <Text size="sm">
                      {formatDate(task.from, i18n.language)}
                      {task.from.slice(0, 10) !== task.to.slice(0, 10) &&
                        ` – ${formatDate(task.to, i18n.language)}`}
                    </Text>
                  </Box>
                  <Box style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <IconClock size={18} color="var(--mantine-color-dimmed)" />
                    <Text size="sm">
                      {formatTime(task.from)} – {formatTime(task.to)}
                    </Text>
                  </Box>
                </Stack>

                {task.notes && (
                  <>
                    <Divider />
                    <Stack gap="xs">
                      <Text size="sm" fw={600} c="dimmed" tt="uppercase">
                        {t("publicTask.notes")}
                      </Text>
                      <Text size="sm" style={{ whiteSpace: "pre-wrap" }}>
                        {task.notes}
                      </Text>
                    </Stack>
                  </>
                )}

                {task.next_booking && (
                  <>
                    <Divider />
                    <Stack gap="xs">
                      <Text size="sm" fw={600} c="dimmed" tt="uppercase">
                        {t("publicTask.nextBooking")}
                      </Text>
                      <Text size="sm">
                        {task.next_booking.guest_first_name ?? "Guest"} · {t("publicTask.checkIn")}{" "}
                        {task.next_booking.from}
                        {task.next_booking.arrival_time && ` at ${formatTime(task.next_booking.arrival_time)}`}
                      </Text>
                      <Text size="xs" fw={600} c="dimmed" tt="uppercase" pt="sm">
                        {t("publicTask.guestsInfo")}
                      </Text>
                      <Text size="sm">
                        {(() => {
                          const b = task.next_booking!;
                          const parts = [
                            b.adults_count ? t("publicTask.adults", { count: b.adults_count }) : null,
                            b.children_count ? t("publicTask.children", { count: b.children_count }) : null,
                            b.infants_count ? t("publicTask.infants", { count: b.infants_count }) : null,
                          ].filter(Boolean);
                          return parts.length > 0 ? parts.join(", ") : t("publicTask.unknownGuests");
                        })()}
                      </Text>
                      <Text size="xs" fw={600} c="dimmed" tt="uppercase" pt="sm">
                        {t("publicTask.specialRequests")}
                      </Text>
                      <Text size="sm" style={{ whiteSpace: "pre-wrap" }}>
                        {task.next_booking.special_requests?.trim() || t("publicTask.noSpecialRequests")}
                      </Text>
                    </Stack>
                  </>
                )}
              </Stack>
            )}
          </Card>

          <Text ta="center" size="xs" c="dimmed">
            {t("publicTask.poweredBy")}
          </Text>
        </Stack>
      </Center>
    </Container>
  );
}
