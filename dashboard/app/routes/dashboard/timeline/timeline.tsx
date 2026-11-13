import { Container } from "@mantine/core";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import { redirect, useLoaderData, useRevalidator } from "react-router";
import { ActivityTimeline } from "~/components/activity-timeline";
import { BookingDetailsPanel } from "~/components/booking-details-panel";
import { TaskDetailsPanel } from "~/components/task-details-panel";
import { LoaderError } from "~/components/loader-error";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/timeline";
import { ContentHeader } from "~/components/content-header";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Timeline" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );

    const [activities, properties] = await Promise.all([
      apiClient.getTimeline(),
      apiClient.getProperties(),
    ]);

    if (properties.data.properties.length === 0) {
      return redirect("/welcome");
    }

    return {
      timeline: activities.data.timeline,
      error: null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return { timeline: [], error: apiError.message };
  }
}

export default function DashboardHome() {
  const { timeline, error } = useLoaderData<typeof loader>();
  const revalidator = useRevalidator();
  const { t } = useTranslation();
  const [selectedBookingId, setSelectedBookingId] = useState<string | null>(
    null,
  );
  const [panelOpened, setPanelOpened] = useState(false);
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [taskPanelOpened, setTaskPanelOpened] = useState(false);

  if (error) {
    return <LoaderError title={t("timeline.title")} />;
  }

  return (
    <Container fluid={true}>
      <ContentHeader
        title={t("timeline.title")}
        subtitle={t("timeline.subtitle")}
        marginBottom={0}
      />

      {/* Timeline - always full width */}
      <ActivityTimeline
        timeline={timeline}
        onBookingClick={(bookingId) => {
          setSelectedBookingId(bookingId);
          setPanelOpened(true);
        }}
        onTaskClick={(taskId) => {
          setSelectedTaskId(taskId);
          setTaskPanelOpened(true);
        }}
      />

      {/* Details Panel - drawer overlay */}
      <BookingDetailsPanel
        bookingId={selectedBookingId}
        opened={panelOpened}
        onClose={() => {
          setPanelOpened(false);
          setSelectedBookingId(null);
        }}
        onUpdate={() => revalidator.revalidate()}
      />

      <TaskDetailsPanel
        taskId={selectedTaskId}
        opened={taskPanelOpened}
        onClose={() => {
          setTaskPanelOpened(false);
          setSelectedTaskId(null);
        }}
        onUpdate={() => revalidator.revalidate()}
      />
    </Container>
  );
}
