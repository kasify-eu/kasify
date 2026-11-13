import {useEffect, useState} from "react";
import {ActionIcon, Button, Container, Group, SimpleGrid} from "@mantine/core";
import {redirect, useLoaderData, useNavigate, useSearchParams} from "react-router";
import { useTranslation } from "react-i18next";
import {ApiClient, createApiConfig, handleApiError} from "~/lib/api-client";
import type {Route} from "./+types/properties";
import {ContentHeader} from "~/components/content-header";
import {LoaderError} from "~/components/loader-error";
import {Property} from "~/components/property";
import {EmptyState} from "~/components/empty-state/empty-state";
import {MainButton} from "~/components/main-button/main-button";
import {PropertyJoinModal, type PropertyJoinInitialState} from "~/components/property-join-modal";
import {IconPlus, IconTicket} from "@tabler/icons-react";

export async function loader({ request }: Route.LoaderArgs) {
  const apiClient = await ApiClient.createAuthenticated(
    request,
    createApiConfig(),
  );

  const url = new URL(request.url);
  const invitationToken = url.searchParams.get("invitation");

  let invitationResult: PropertyJoinInitialState | null = null;
  if (invitationToken) {
    try {
      const res = await apiClient.acceptInvitation(invitationToken);
      invitationResult = {
        status: "success",
        propertyName: res.data.propertyName,
        propertyId: res.data.propertyId,
      };
    } catch (error) {
      const apiError = handleApiError(error);
      invitationResult = {
        status: "error",
        code: apiError.code ?? null,
        message: apiError.message,
      };
    }
  }

  try {
    const response = await apiClient.getProperties();
    return { data: response.data, error: null, invitationResult };
  } catch (error) {
    const apiError = handleApiError(error);
    return { data: null, error: apiError.message, invitationResult };
  }
}

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Properties" }];
}

export async function action({ request }: Route.ActionArgs) {
  try {
    const formData = await request.formData();
    const intent = formData.get("intent");

    if (intent === "delete") {
      const propertyId = formData.get("propertyId") as string;

      if (!propertyId) {
        return { error: "Property ID is required", success: false };
      }

      const apiClient = await ApiClient.createAuthenticated(
        request,
        createApiConfig(),
      );
      await apiClient.deleteProperty(propertyId);

      return redirect("/properties");
    }

    return { error: "Unknown action", success: false };
  } catch (error) {
    const apiError = handleApiError(error);
    return { error: apiError.message, success: false };
  }
}

export default function DashboardProperties() {
  const { data, error, invitationResult } = useLoaderData<typeof loader>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [joinModalOpened, setJoinModalOpened] = useState(!!invitationResult);

  useEffect(() => {
    if (invitationResult && searchParams.has("invitation")) {
      const next = new URLSearchParams(searchParams);
      next.delete("invitation");
      setSearchParams(next, { replace: true });
    }
    // Only runs once per loader response; subsequent searchParams changes are irrelevant.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [invitationResult]);

  if (error || !data) {
    return <LoaderError title={t("properties.title")} />;
  }

  return (
    <Container fluid={true}>
      <ContentHeader
        title={t("properties.title")}
        subtitle={t("properties.subtitle")}
        right={
          <Group gap="sm">
            <MainButton
                leftSection={<IconPlus size={18}/>}
                onClick={() => navigate("/properties/new")}
                visibleFrom="sm"
            >
              {t("properties.add")}
            </MainButton>
            <ActionIcon
                variant="filled"
                color="dark"
                size="lg"
                radius="xl"
                onClick={() => navigate("/properties/new")}
                hiddenFrom="sm"
            >
              <IconPlus size={18}/>
            </ActionIcon>
            <Button
                variant="outline"
                size="sm"
                radius="lg"
                leftSection={<IconTicket size={18}/>}
                onClick={() => setJoinModalOpened(true)}
                visibleFrom="sm"
            >
              {t("properties.join")}
            </Button>
            <ActionIcon
                variant="outline"
                size="lg"
                radius="xl"
                onClick={() => setJoinModalOpened(true)}
                hiddenFrom="sm"
            >
              <IconTicket size={18}/>
            </ActionIcon>
          </Group>
        }
      />

      <PropertyJoinModal
          opened={joinModalOpened}
          onClose={() => setJoinModalOpened(false)}
          initialState={invitationResult}
      />

      {data.properties.length === 0 ? (
        <EmptyState
          title={t("properties.emptyTitle")}
          image="/icons/house-duotone.svg"
          subtitle={t("properties.emptySubtitle")}
        />
      ) : (
          <SimpleGrid
              cols={{base: 1, sm: 1, md: 2, lg: 3, xl: 4}}
              spacing="xl"
              pb="xl"
          >
          {data.properties.map((property) => (
            <Property key={property.id} property={property} />
          ))}
          </SimpleGrid>
      )}
    </Container>
  );
}
