import {
  Badge,
  Box,
  Group,
  Stack,
  Text,
  ThemeIcon,
} from "@mantine/core";
import { useTranslation } from "react-i18next";
import { IconCalendarEvent, IconClock, IconUser } from "@tabler/icons-react";
import classes from "~/components/details-drawer/details-drawer.module.css";

interface NextBookingSectionProps {
  sectionNum: string;
  guestName: string;
  guestSummary: string;
  dateRange: string;
  arrivalTime?: string | null;
  channel?: string | null;
  specialRequests: string | null;
}

export function NextBookingSection({
  sectionNum,
  guestName,
  guestSummary,
  dateRange,
  arrivalTime,
  channel,
  specialRequests,
}: NextBookingSectionProps) {
  const { t } = useTranslation();
  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("cleaningDetails.nextBooking") as string}
        </div>
      </div>

      <Box
        style={{
          border: "1px solid var(--mantine-color-gray-3)",
          borderRadius: "var(--mantine-radius-lg)",
          overflow: "hidden",
        }}
      >
        <Group justify="space-between" wrap="wrap" gap="md" p="md">
          <Group gap="sm">
            <ThemeIcon variant="light" color="orange" size="lg" radius="xl">
              <IconUser size={18} />
            </ThemeIcon>
            <Stack gap={0}>
              <Text size="sm" fw={700}>
                {guestName}
              </Text>
              {guestSummary && (
                <Text size="xs" c="dimmed">
                  {guestSummary}
                </Text>
              )}
            </Stack>
          </Group>

          {channel && (
            <Badge variant="light" color="gray" size="sm">
              {channel}
            </Badge>
          )}
        </Group>

        <Box
          px="md"
          py="sm"
          style={{
            borderTop: "1px solid var(--mantine-color-gray-2)",
            backgroundColor: "var(--mantine-color-gray-0)",
          }}
        >
          <Group gap="lg">
            <Group gap="xs">
              <IconCalendarEvent
                size={16}
                color="var(--mantine-color-dimmed)"
              />
              <Text size="sm" fw={500} c="dimmed">
                {dateRange}
              </Text>
            </Group>
            {arrivalTime && (
              <Group gap="xs">
                <IconClock size={16} color="var(--mantine-color-dimmed)" />
                <Text size="sm" fw={500} c="dimmed">
                  {t("cleaningDetails.checkInTime")} {arrivalTime}
                </Text>
              </Group>
            )}
          </Group>
        </Box>
      </Box>

      <Text fw={600} size="sm" mt="lg" mb="xs">
        {t("cleaningDetails.specialRequests")}
      </Text>
      <Text size="sm" c={specialRequests ? undefined : "dimmed"}>
        {specialRequests || t("cleaningDetails.noSpecialRequests")}
      </Text>
    </div>
  );
}
