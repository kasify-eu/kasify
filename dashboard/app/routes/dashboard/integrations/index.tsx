import {Box, Button, Container, Group, SimpleGrid, Text,} from "@mantine/core";
import {IntegrationSelectionModal} from "~/components/integration-selection-modal";
import {useState} from "react";
import {useLoaderData, useNavigate} from "react-router";
import { useTranslation } from "react-i18next";
import {EmptyState} from "~/components/empty-state/empty-state";
import {LoaderError} from "~/components/loader-error";
import {ApiClient, createApiConfig, handleApiError} from "~/lib/api-client";
import type {Route} from "./+types/index";
import type {IntegrationsResponse} from "~/types/integrations";
import {IconPlus} from "@tabler/icons-react";
import {ContentHeader} from "~/components/content-header";
import {MainButton} from "~/components/main-button/main-button";

export async function loader({ request }: Route.LoaderArgs) {
  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const response = await apiClient.getIntegrations();
    return { data: response.data, error: null };
  } catch (error) {
    const apiError = handleApiError(error);
    return { data: null, error: apiError.message };
  }
}

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Integrations" }];
}

type IntegrationCardProps = {
  id: string;
  name: string;
  description: string;
  image: string;
  category: string;
  accentColor: string;
  onClick: () => void;
};

function IntegrationCard({
  name,
  description,
  image,
  category,
  accentColor,
  onClick,
}: IntegrationCardProps) {
  const { t } = useTranslation();
  return (
    <Box
      style={{
        position: "relative",
        backgroundColor: "white",
        borderRadius: 20,
        paddingTop: 12,
        paddingLeft: 32,
        paddingRight: 32,
        paddingBottom: 32,
        border: "1px solid #E1E5E9",
        transition: "border-color 150ms ease, box-shadow 150ms ease",
      }}
      className="integration-card"
    >
      {/* Accent bar at top */}
      <Box
        style={{
          position: "absolute",
          top: 0,
          left: "50%",
          transform: "translateX(-50%)",
          width: 96,
          height: 4,
          backgroundColor: accentColor,
          borderRadius: "0 0 4px 4px",
        }}
      />

      {/* Header with logo and status */}
      <Group justify="space-between" align="flex-start" mb={12}>
        <Box
          style={{
            height: 64,
              width: "auto",
              display: "flex",
              paddingTop: 12,
            alignItems: "center",
            justifyContent: "center",
              borderRadius: 0,
            overflow: "hidden",
          }}
        >
          <img
            src={image}
            alt={name}
            style={{
                height: "100%",
                width: "auto",
                maxWidth: "160px",
                objectFit: "contain",
            }}
          />
        </Box>
      </Group>

      {/* Footer */}
      <Group justify="space-between" align="center">
        <Text
          size="xs"
          fw={500}
          c="dimmed"
          style={{ textTransform: "uppercase", letterSpacing: "0.1em" }}
        >
          {category}
        </Text>
        <Button
          variant="outline"
          color="gray"
          radius="xl"
          size="sm"
          onClick={onClick}
          styles={{
            root: {
              fontWeight: 600,
              paddingLeft: 24,
              paddingRight: 24,
            },
          }}
        >
          {t("integrations.manage")}
        </Button>
      </Group>

    </Box>
  );
}

// Integration metadata for display
const INTEGRATION_METADATA: Record<
  string,
  { name: string; category: string; accentColor: string; image: string }
> = {
  nuki: {
    name: "Nuki Smart Lock",
    category: "Smart Lock",
    accentColor: "#FACC15",
    image: "/imgs/brands/nuki.svg",
  },
  unifi: {
    name: "Unifi Access",
    category: "Smart Lock",
    accentColor: "#3B82F6",
      image: "/imgs/brands/unifi.svg",
  },
  ses_hospedajes: {
    name: "SES Hospedajes",
    category: "Guest Registry",
    accentColor: "#DC2626",
    image: "/imgs/brands/ses-hospedajes.svg",
  },
};

function getConfiguredIntegrations(data: IntegrationsResponse) {
  const result: {
    id: string;
    name: string;
    description: string;
    image: string;
    category: string;
    accentColor: string;
    path: string;
  }[] = [];
  for (const item of data.integrations) {
    const meta = INTEGRATION_METADATA[item.type];
    if (!meta) continue;

    let description = "Integration configured";
    switch (item.type) {
      case "nuki":
        description = "Smart lock integration configured";
        break;
      case "unifi":
        description = item.unifi_username
          ? `Connected as ${item.unifi_username}`
          : "Network smart locks configured";
        break;
      case "ses_hospedajes":
        description = item.ses_username
          ? `Connected as ${item.ses_username}`
          : "SES Hospedajes integration configured";
        break;
    }

    const pathSegment = item.type.replace(/_/g, "-");
    result.push({
      id: `${item.type}-${item.id}`,
      name: meta.name,
      description,
      image: meta.image,
      category: meta.category,
      accentColor: meta.accentColor,
      path: `/integrations/${pathSegment}/${item.id}`,
    });
  }
  return result;
}

export default function IntegrationsPage() {
  const { data, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();
  const [modalOpened, setModalOpened] = useState(false);
  const navigate = useNavigate();

  const handleIntegrationSelect = (integrationId: string) => {
    setModalOpened(false);
    if (integrationId === "nuki") {
      navigate("/integrations/nuki");
    } else if (integrationId === "unifi") {
        navigate("/integrations/unifi");
    } else if (integrationId === "ses-hospedajes") {
        navigate("/integrations/ses-hospedajes");
    }
  };

  if (error) {
    return <LoaderError title={t("integrations.title")} />;
  }

  const configuredIntegrations = data ? getConfiguredIntegrations(data) : [];
  const hasIntegrations = configuredIntegrations.length > 0;

  return (
    <Container fluid={true}>
      {/* Header */}
      <ContentHeader title={t("integrations.title")} subtitle={t("integrations.subtitle")} right={
          <MainButton
              leftSection={<IconPlus size={18} />}
              onClick={() => setModalOpened(true)}
          >
              {t("integrations.add")}
          </MainButton>
      } />

      {/* Integration cards grid */}
      <style>{`
        .integration-card:hover {
          border-color: #CBD5E1;
          box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
        }
      `}</style>
      {hasIntegrations ? (
        <SimpleGrid cols={{ base: 1, md: 2, xl: 6 }} spacing={32}>
          {configuredIntegrations.map((integration) => (
            <IntegrationCard
              key={integration.id}
              id={integration.id}
              name={integration.name}
              description={integration.description}
              image={integration.image}
              category={integration.category}
              accentColor={integration.accentColor}
              onClick={() => navigate(integration.path!)}
            />
          ))}
        </SimpleGrid>
      ) : (
        <EmptyState
          title={t("integrations.emptyTitle")}
          image="/icons/gears.svg"
          subtitle={t("integrations.emptySubtitle")}
        />
      )}

      <IntegrationSelectionModal
        opened={modalOpened}
        onClose={() => setModalOpened(false)}
        onSelect={handleIntegrationSelect}
      />
    </Container>
  );
}
