import {ActionIcon, Box, Button, Container, Divider, Modal, SimpleGrid, Stack, Text, TextInput, Title} from "@mantine/core";
import {useDisclosure} from "@mantine/hooks";
import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {useTranslation} from "react-i18next";
import {ApiClient, ApiClientError, createApiConfig} from "~/lib/api-client";
import {SecondaryButton} from "~/components/secondary-button/secondary-button";
import {IconTrash,} from "@tabler/icons-react";
import type {AvailableNukiSmartlock, NukiStatus} from "~/types/integrations";
import type {Property} from "~/types/properties";
import {ContentHeader} from "~/components/content-header";
import {ErrorMessage} from "~/components/error-message";
import {NukiSmartlockCard} from "~/components/nuki-smartlock-card";
import {MainButton} from "~/components/main-button/main-button";
import {showErrorToast, showSuccessToast} from "~/lib/toast";

export default function NukiIntegrationDetails() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id: integrationId } = useParams<{ id: string }>();
  const [deleteModalOpened, { open: openDeleteModal, close: closeDeleteModal }] =
    useDisclosure(false);

  const [smartlocks, setSmartlocks] = useState<AvailableNukiSmartlock[]>([]);
  const [properties, setProperties] = useState<Property[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nukiStatus, setNukiStatus] = useState<NukiStatus>("online");
  const [nukiToken, setNukiToken] = useState("");
  const [isUpdatingToken, setIsUpdatingToken] = useState(false);
  const [newToken, setNewToken] = useState("");
  const [isSavingToken, setIsSavingToken] = useState(false);

  const loadData = async () => {
    setIsLoading(true);
    setError(null);
    setNukiStatus("online");

    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());

      const [smartlocksResponse, propertiesResponse] = await Promise.all([
        apiClient.getNukiSmartlocks(integrationId!),
        apiClient.getProperties(),
      ]);

      setSmartlocks(smartlocksResponse.data.smartlocks);
      setProperties(propertiesResponse.data.properties);
      setNukiStatus(smartlocksResponse.data.nuki_status);
    } catch {
      setError(t("nukiDetails.loadError"));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAssign = async (
    lock: AvailableNukiSmartlock,
    propertyId: string,
  ) => {
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.assignSmartlockToProperty(propertyId, {
        name: lock.name,
        nuki_smartlock_id: lock.smartlock_id.toString(),
        nuki_integration_id: integrationId!,
      });
      await loadData();
    } catch (err: any) {
      setError(err?.message || t("nukiDetails.assignError"));
        throw err;
    }
  };

  const handleUnassign = async (lock: AvailableNukiSmartlock) => {
    if (!lock.assigned_property) return;

    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.unassignSmartlockFromProperty(
        lock.assigned_property.property_id,
        lock.assigned_property.smartlock_record_id,
      );
      await loadData();
    } catch (err: any) {
      setError(err?.message || t("nukiDetails.unassignError"));
        throw err;
    }
  };

  const handleSaveToken = async () => {
    setIsSavingToken(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.updateNukiIntegration(integrationId!, newToken.trim());
      setNewToken("");
      showSuccessToast(t("nukiDetails.toast.tokenUpdated"));
      await loadData();
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `nukiDetails.error.${code}` : null;
      showErrorToast(key && t(key) !== key ? t(key) : t("nukiDetails.updateTokenError"));
    } finally {
      setIsSavingToken(false);
    }
  };

  const handleDeleteIntegration = async () => {
    setIsDeleting(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.deleteNukiIntegration(integrationId!);
      closeDeleteModal();
      navigate("/integrations");
    } catch (err: any) {
      setError(err?.message || t("nukiDetails.deleteError"));
    } finally {
      setIsDeleting(false);
    }
  };

  const breadcrumbItems = [
    { title: t("nav.integrations"), href: "/integrations" },
    { title: "Nuki", href: "#" },
  ];

  return (
    <Container fluid={true}>
      <ContentHeader
        breadcrumbs={breadcrumbItems}
        subtitle={t("nukiDetails.subtitle")}
        right={
          <>
            <SecondaryButton
              color="red"
              leftSection={<IconTrash size={16} />}
              onClick={openDeleteModal}
              visibleFrom="sm"
            >
              {t("nukiDetails.removeButton")}
            </SecondaryButton>
            <ActionIcon
              variant="outline"
              color="red"
              size="lg"
              radius="xl"
              onClick={openDeleteModal}
              hiddenFrom="sm"
            >
              <IconTrash size={16} />
            </ActionIcon>
          </>
        }
      />

      {/* Fatal load error */}
      {error && (
        <ErrorMessage
          title={t("nukiDetails.loadErrorTitle")}
          message={error}
        />
      )}

      {/* Nuki API status warning (locks still shown from DB) */}
      {nukiStatus === "unauthorized" && (
        <ErrorMessage
          title={t("nukiDetails.invalidTokenTitle")}
          message={t("nukiDetails.invalidTokenMessage")}
          mb="36px"
        >
          <Stack gap="sm" maw="400px">
            <TextInput
              size="md"
              placeholder={t("nukiDetails.tokenPlaceholder")}
              value={nukiToken}
              label="Available in web.nuki.io under Settings > API Token"
              onChange={(e) => setNukiToken(e.currentTarget.value)}
            />
            <MainButton
                maw="200px"
                loading={isUpdatingToken}
                disabled={!nukiToken.trim()}
                onClick={async () => {
                  setIsUpdatingToken(true);
                  try {
                    const apiClient = ApiClient.createFromBrowser(createApiConfig());
                    await apiClient.updateNukiIntegration(integrationId!, nukiToken);
                    await loadData();
                  } finally {
                    setIsUpdatingToken(false);
                  }
                }}
            >
              {t("nukiDetails.updateToken")}
            </MainButton>
          </Stack>
        </ErrorMessage>
      )}
      {nukiStatus === "unreachable" && (
        <ErrorMessage
          title={t("nukiDetails.unreachableTitle")}
          message={t("nukiDetails.unreachableMessage")}
        />
      )}

      {/* Loading state */}
      {!error && isLoading ? (
        <Text c="dimmed" size="sm">
          {t("nukiDetails.loading")}
        </Text>
      ) : !error && smartlocks.length === 0 ? (
        <></>
      ) : !error ? (
        /* Smart locks grid */
        <Stack gap={16}>
          <Box>
            <Title order={3} mb={4}>
              {t("nukiDetails.locks.title")}
            </Title>
            <Text size="sm" c="dimmed">
              {t("nukiDetails.locks.description")}
            </Text>
          </Box>
          <SimpleGrid cols={{ base: 1, md: 2, xl: 6 }} spacing={32}>
            {smartlocks.map((lock) => (
              <NukiSmartlockCard
                key={lock.smartlock_id}
                lock={lock}
                properties={properties}
                onAssign={handleAssign}
                onUnassign={handleUnassign}
                isLoading={isLoading}
              />
            ))}
          </SimpleGrid>
        </Stack>
      ) : null}

      {!isLoading && (
        <>
          <Divider my={48} />
          <Stack gap={16}>
            <Box>
              <Title order={3} mb={4}>
                {t("nukiDetails.credentials.title")}
              </Title>
              <Text size="sm" c="dimmed">
                {t("nukiDetails.credentials.description")}
              </Text>
            </Box>
            <Box
              style={{
                backgroundColor: "white",
                borderRadius: 16,
                padding: 32,
                maxWidth: 920,
              }}
            >
              <Stack gap="lg">
                <TextInput
                  label={t("nukiDetails.credentials.tokenLabel")}
                  description={t("nukiDetails.credentials.tokenDescription")}
                  placeholder={t("nukiDetails.tokenPlaceholder")}
                  value={newToken}
                  onChange={(e) => setNewToken(e.currentTarget.value)}
                  required
                />
                <div>
                  <MainButton
                    loading={isSavingToken}
                    disabled={!newToken.trim()}
                    onClick={handleSaveToken}
                  >
                    {t("nukiDetails.credentials.save")}
                  </MainButton>
                </div>
              </Stack>
            </Box>
          </Stack>
        </>
      )}

      {/* Delete confirmation modal */}
      <Modal
        opened={deleteModalOpened}
        onClose={closeDeleteModal}
        withCloseButton={false}
        centered
        size="md"
        radius={32}
        padding={0}
        styles={{
          content: {
            borderRadius: 32,
            overflow: "hidden",
          },
        }}
      >
        <Box className="p-8">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <IconTrash size={24} className="text-red-600" />
              </div>
              <div>
                <Text
                  size="xl"
                  fw={700}
                  className="tracking-tight text-slate-900 dark:text-white"
                >
                  {t("nukiDetails.removeModal.title")}
                </Text>
              </div>
            </div>
            <button
              onClick={closeDeleteModal}
              className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors"
            >
              <svg
                className="w-5 h-5 text-slate-400"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>

          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {t("nukiDetails.removeModal.description")}
            </Text>

            <div className="bg-red-50 border border-red-200 rounded-xl p-4">
              <Text size="sm" fw={600} c="red.7" mb={8}>
                {t("nukiDetails.removeModal.warningHeader")}
              </Text>
              <ul className="text-sm text-red-700 space-y-1 ml-4 list-disc">
                <li>{t("nukiDetails.removeModal.item1")}</li>
                <li>{t("nukiDetails.removeModal.item2")}</li>
                <li>{t("nukiDetails.removeModal.item3")}</li>
              </ul>
            </div>

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={closeDeleteModal}
                styles={{
                  root: {
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("nukiDetails.removeModal.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={isDeleting}
                onClick={handleDeleteIntegration}
                styles={{
                  root: {
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("nukiDetails.removeModal.confirm")}
              </Button>
            </div>
          </Stack>
        </Box>
      </Modal>
    </Container>
  );
}
