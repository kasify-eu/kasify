import {ContentHeader} from "~/components/content-header";
import {Box, Container, Group, Image, Stack, Text, Title,} from "@mantine/core";
import {useNavigate} from "react-router";
import {ApiClient, createApiConfig} from "~/lib/api-client";
import {NukiTokenForm} from "~/components/nuki-token-form";

export default function NukiIntegration() {
  const navigate = useNavigate();

  const handleSubmit = async (nukiToken: string) => {
    const apiClient = ApiClient.createFromBrowser(createApiConfig());
    const response = await apiClient.createNukiIntegration(nukiToken);
    navigate(`/integrations/nuki/${response.data.id}`);
  };

  const handleCancel = () => {
    navigate("/integrations");
  };

  const breadcrumbItems = [
    {title: "Integrations", href: "/integrations"},
    {title: "Nuki", href: "#"},
  ];

  return (
    <Container fluid={true}>
      <ContentHeader
        breadcrumbs={breadcrumbItems}
        subtitle="Connect your Nuki account to manage smart locks"
      />

      <Group align="flex-start" gap={32} wrap="wrap">
        <NukiTokenForm onSubmit={handleSubmit} onCancel={handleCancel} />

        {/* Help Panel */}
        <Box
          style={{
            backgroundColor: "var(--mantine-color-gray-0)",
            borderRadius: 12,
            padding: 24,
            width: 560,
            minWidth: 320,
          }}
        >
          <Stack gap="md">
            <Title order={5}>How to get your Nuki API Token</Title>
            <Text size="sm" fw="700" mt="lg">
              1. Open web.nuki.io and access the API page
            </Text>
            <Image src="/imgs/nuki_1.jpg" alt="Nuki Integration" style={{maxWidth: "480px"}}/>
            <Text size="sm" fw="700" mt="lg">
              2. Create a token and copy it
            </Text>
            <Image src="/imgs/nuki_2.jpg" alt="Nuki Integration" style={{maxWidth: "480px"}}/>
          </Stack>
        </Box>
      </Group>
    </Container>
  );
}
