import {
    ActionIcon,
    Box,
    Button,
    Container,
    Divider,
    Modal,
    SimpleGrid,
    Stack,
    Switch,
    Text,
    Title,
} from "@mantine/core";
import {useDisclosure} from "@mantine/hooks";
import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {useTranslation} from "react-i18next";
import {ApiClient, ApiClientError, createApiConfig} from "~/lib/api-client";
import {ContentHeader} from "~/components/content-header";
import {SecondaryButton} from "~/components/secondary-button/secondary-button";
import {MainButton} from "~/components/main-button/main-button";
import {ErrorMessage} from "~/components/error-message";
import {
    SesHospedajesCredentialsCard,
    translateSesHospedajesError,
} from "~/components/ses-hospedajes-credentials-card";
import {SesPropertyEstablishmentCodeCard} from "~/components/ses-property-establishment-code-card";
import {showErrorToast, showSuccessToast} from "~/lib/toast";
import {IconTrash} from "@tabler/icons-react";
import type {SesHospedajesIntegration} from "~/types/integrations";
import type {Property} from "~/types/properties";

export default function SesHospedajesDetails() {
    const {id} = useParams();
    const navigate = useNavigate();
    const {t} = useTranslation();
    const [deleteModalOpened, {open: openDeleteModal, close: closeDeleteModal}] =
        useDisclosure(false);

    const [integration, setIntegration] = useState<SesHospedajesIntegration | null>(null);
    const [properties, setProperties] = useState<Property[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isDeleting, setIsDeleting] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Form state
    const [arrendadorCode, setArrendadorCode] = useState("");
    const [sesUsername, setSesUsername] = useState("");
    const [sesPassword, setSesPassword] = useState("");
    const [rhEnabled, setRhEnabled] = useState(true);
    const [isSavingRh, setIsSavingRh] = useState(false);

    const loadData = async () => {
        setIsLoading(true);
        setError(null);

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            const [integrationsResponse, propertiesResponse] = await Promise.all([
                apiClient.getSesHospedajesIntegrations(),
                apiClient.getProperties(),
            ]);
            const found = integrationsResponse.data.find((i) => i.id === id);
            if (found) {
                setIntegration(found);
                setArrendadorCode(found.arrendador_code);
                setSesUsername(found.ses_username);
                setSesPassword("");
                setRhEnabled(found.rh_direct_bookings_enabled);
            } else {
                setError(t("sesHospedajesDetails.notFound"));
            }
            setProperties(propertiesResponse.data.properties);
        } catch (err: any) {
            console.error("Error loading data:", err);
            setError(t("sesHospedajesDetails.loadError"));
        } finally {
            setIsLoading(false);
        }
    };

    const handleSaveEstablishmentCode = async (propertyId: string, code: string) => {
        if (!id) return;
        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            // The assignment travels with the code: saving from this page configures the property
            // on THIS integration (rejected with ses_hospedajes.property_already_configured if it
            // belongs to another one — those cards are locked below, but the API enforces it too).
            const response = await apiClient.patchProperty(propertyId, {
                ses_establishment_code: code,
                ses_integration_id: id,
            });
            setProperties((prev) =>
                prev.map((p) => (p.id === propertyId ? response.data : p)),
            );
            showSuccessToast(t("sesEstablishmentCodeCard.toast.saved"), "🏨");
        } catch (err) {
            console.error("Error saving establishment code:", err);
            const errorCode = err instanceof ApiClientError ? err.code : null;
            const key = errorCode ? `sesEstablishmentCodeCard.error.${errorCode}` : null;
            const translated =
                key && t(key) !== key ? t(key) : t("sesEstablishmentCodeCard.saveError");
            showErrorToast(translated);
            throw err;
        }
    };

    const handleToggleRh = async (checked: boolean) => {
        if (!id) return;

        const previous = rhEnabled;
        setRhEnabled(checked); // optimistic
        setIsSavingRh(true);
        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.updateSesHospedajesSettings(id, {
                rh_direct_bookings_enabled: checked,
            });
            showSuccessToast(t("sesHospedajesDetails.reservas.toast.updated"));
        } catch (err) {
            console.error("Error updating Reserva de Hospedaje setting:", err);
            setRhEnabled(previous); // revert
            showErrorToast(t("sesHospedajesDetails.reservas.saveError"));
        } finally {
            setIsSavingRh(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleSave = async () => {
        if (!id) return;

        setIsSaving(true);
        setError(null);

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.updateSesHospedajesIntegration(id, {
                arrendador_code: arrendadorCode,
                ses_username: sesUsername,
                ses_password: sesPassword,
            });
            // Saving from a flagged state clears the pause optimistically: SES only verified the
            // login, so don't claim "fixed" — submissions retry within the hour and SES re-notifies
            // if the configuration is still wrong.
            showSuccessToast(
                integration?.credentials_error_code != null
                    ? t("sesHospedajesDetails.toast.updatedAfterError")
                    : t("sesHospedajesDetails.toast.updated"),
            );
            navigate("/integrations");
            return;
        } catch (err: any) {
            console.error("Error updating integration:", err);
            setError(translateSesHospedajesError(err, t, "sesHospedajesDetails.updateError"));
        } finally {
            setIsSaving(false);
        }
    };

    const handleDeleteIntegration = async () => {
        if (!id) return;

        setIsDeleting(true);
        setError(null);
        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.deleteSesHospedajesIntegration(id);
            closeDeleteModal();
            navigate("/integrations");
        } catch (err: any) {
            console.error("Error deleting integration:", err);
            setError(t("sesHospedajesDetails.deleteError"));
        } finally {
            setIsDeleting(false);
        }
    };

    const breadcrumbItems = [
        {title: t("nav.integrations"), href: "/integrations"},
        {title: t("sesHospedajesDetails.breadcrumb"), href: "#"},
    ];

    return (
        <Container fluid={true}>
            <ContentHeader
                breadcrumbs={breadcrumbItems}
                subtitle={t("sesHospedajesDetails.subtitle")}
                right={
                    <>
                        <SecondaryButton
                            color="red"
                            leftSection={<IconTrash size={16}/>}
                            onClick={openDeleteModal}
                            visibleFrom="sm"
                        >
                            {t("sesHospedajesDetails.removeButton")}
                        </SecondaryButton>
                        <ActionIcon
                            variant="outline"
                            color="red"
                            size="lg"
                            radius="xl"
                            onClick={openDeleteModal}
                            hiddenFrom="sm"
                        >
                            <IconTrash size={16}/>
                        </ActionIcon>
                    </>
                }
            />

            {error && <ErrorMessage message={error}/>}

            {isLoading ? (
                <Text c="dimmed" size="sm">
                    {t("sesHospedajesDetails.loading")}
                </Text>
            ) : integration ? (
                <Stack gap={48}>
                    {integration.credentials_error_code != null && (
                        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
                            <Text size="sm" fw={600} c="red.7" mb={4}>
                                {t("sesHospedajesDetails.credentialsError.title", {
                                    code: integration.credentials_error_code,
                                })}
                            </Text>
                            {integration.credentials_error_message && (
                                <Text size="sm" c="red.7" mb={4}>
                                    {integration.credentials_error_message}
                                </Text>
                            )}
                            <Text size="sm" c="red.7">
                                {t("sesHospedajesDetails.credentialsError.description")}
                            </Text>
                        </div>
                    )}
                    <Stack gap={16}>
                        <Box>
                            <Title order={3} mb={4}>
                                {t("sesHospedajesDetails.establishmentCodes.title")}
                            </Title>
                            <Text size="sm" c="dimmed">
                                {t("sesHospedajesDetails.establishmentCodes.description")}
                            </Text>
                        </Box>
                        {properties.length === 0 ? (
                            <Text size="sm" c="dimmed">
                                {t("sesHospedajesDetails.establishmentCodes.emptyState")}
                            </Text>
                        ) : (
                            <SimpleGrid cols={{base: 1, md: 2, xl: 3}} spacing={32}>
                                {properties.map((property) => (
                                    <SesPropertyEstablishmentCodeCard
                                        key={property.id}
                                        property={property}
                                        onSave={handleSaveEstablishmentCode}
                                        // One integration per property: a property configured on a
                                        // different integration is visible but locked here.
                                        locked={
                                            property.ses_integration_id != null &&
                                            property.ses_integration_id !== id
                                        }
                                    />
                                ))}
                            </SimpleGrid>
                        )}
                    </Stack>

                    <Divider/>

                    <Stack gap={16}>
                        <Box>
                            <Title order={3} mb={4}>
                                {t("sesHospedajesDetails.reservas.title")}
                            </Title>
                            <Text size="sm" c="dimmed">
                                {t("sesHospedajesDetails.reservas.description")}
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
                                <Switch
                                    checked={rhEnabled}
                                    onChange={(e) =>
                                        handleToggleRh(e.currentTarget.checked)
                                    }
                                    disabled={isSavingRh}
                                    size="md"
                                    label={t("sesHospedajesDetails.reservas.toggleLabel")}
                                    description={t(
                                        "sesHospedajesDetails.reservas.toggleDescription",
                                    )}
                                />
                                <div className="bg-blue-50 border border-blue-200 rounded-xl p-4">
                                    <Text size="sm" c="blue.7">
                                        {t("sesHospedajesDetails.reservas.info")}
                                    </Text>
                                </div>
                            </Stack>
                        </Box>
                    </Stack>

                    <Divider/>

                    <Stack gap={16}>
                        <Box>
                            <Title order={3} mb={4}>
                                {t("sesHospedajesDetails.credentials.title")}
                            </Title>
                            <Text size="sm" c="dimmed">
                                {t("sesHospedajesDetails.credentials.description")}
                            </Text>
                        </Box>
                        <SesHospedajesCredentialsCard
                            arrendadorCode={arrendadorCode}
                            onArrendadorCodeChange={setArrendadorCode}
                            sesUsername={sesUsername}
                            onSesUsernameChange={setSesUsername}
                            sesPassword={sesPassword}
                            onSesPasswordChange={setSesPassword}
                            passwordDescription={t("sesHospedajesDetails.passwordDescription")}
                            passwordPlaceholder={t("sesHospedajesDetails.passwordPlaceholder")}
                            footer={
                                <MainButton
                                    onClick={handleSave}
                                    loading={isSaving}
                                    disabled={!arrendadorCode.trim() || !sesUsername.trim() || !sesPassword.trim()}
                                >
                                    {t("sesHospedajesDetails.save")}
                                </MainButton>
                            }
                        />
                    </Stack>
                </Stack>
            ) : null}

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
                                <IconTrash size={24} className="text-red-600"/>
                            </div>
                            <div>
                                <Text
                                    size="xl"
                                    fw={700}
                                    className="tracking-tight text-slate-900 dark:text-white"
                                >
                                    {t("sesHospedajesDetails.removeModal.title")}
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
                            {t("sesHospedajesDetails.removeModal.description")}
                        </Text>

                        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
                            <Text size="sm" fw={600} c="red.7" mb={8}>
                                {t("sesHospedajesDetails.removeModal.warningHeader")}
                            </Text>
                            <ul className="text-sm text-red-700 space-y-1 ml-4 list-disc">
                                <li>{t("sesHospedajesDetails.removeModal.item1")}</li>
                                <li>{t("sesHospedajesDetails.removeModal.item2")}</li>
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
                                {t("sesHospedajesDetails.removeModal.cancel")}
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
                                {t("sesHospedajesDetails.removeModal.confirm")}
                            </Button>
                        </div>
                    </Stack>
                </Box>
            </Modal>
        </Container>
    );
}
