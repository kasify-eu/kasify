import {
    ActionIcon,
    Box,
    Button,
    Container,
    Divider,
    Group,
    Modal,
    PasswordInput,
    Select,
    SimpleGrid,
    Stack,
    Text,
    TextInput,
    Title,
} from "@mantine/core";
import {useDisclosure} from "@mantine/hooks";
import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router";
import {useTranslation} from "react-i18next";
import {ApiClient, ApiClientError, createApiConfig} from "~/lib/api-client";
import {MainButton} from "~/components/main-button/main-button";
import {SecondaryButton} from "~/components/secondary-button/secondary-button";
import {IconArrowRight, IconHome, IconLock, IconLockOpen, IconPlus, IconTrash,} from "@tabler/icons-react";
import type {AvailableUnifiSmartlock, UnifiStatus} from "~/types/integrations";
import type {Property} from "~/types/properties";
import {ContentHeader} from "~/components/content-header";
import {showErrorToast, showSuccessToast} from "~/lib/toast";
import {ErrorMessage} from "~/components/error-message";

function getLockStateKey(lockState: string | undefined): string {
    if (!lockState) return "unifiSmartlockCard.state.unknown";
    switch (lockState.toLowerCase()) {
        case "locked":
            return "unifiSmartlockCard.state.locked";
        case "unlocked":
            return "unifiSmartlockCard.state.unlocked";
        case "online":
            return "unifiSmartlockCard.state.online";
        case "offline":
            return "unifiSmartlockCard.state.offline";
        default:
            return "unifiSmartlockCard.state.unknown";
    }
}

function getLockStateColors(lockState: string | undefined): { bg: string; text: string; dot: string } {
    if (!lockState) {
        return {bg: "#F1F5F9", text: "#64748B", dot: "#94A3B8"};
    }
    switch (lockState.toLowerCase()) {
        case "locked":
            return {bg: "#D1FAE5", text: "#047857", dot: "#10B981"};
        case "unlocked":
            return {bg: "#FEF3C7", text: "#B45309", dot: "#F59E0B"};
        case "offline":
            return {bg: "#FEE2E2", text: "#DC2626", dot: "#EF4444"};
        case "online":
            return {bg: "#DBEAFE", text: "#1D4ED8", dot: "#3B82F6"};
        default:
            return {bg: "#F1F5F9", text: "#64748B", dot: "#94A3B8"};
    }
}

function isLockOpen(lockState: string | undefined): boolean {
    if (!lockState) return false;
    return lockState.toLowerCase() === "unlocked";
}

interface SmartlockCardProps {
    lock: AvailableUnifiSmartlock;
    properties: Property[];
    onAssign: (lock: AvailableUnifiSmartlock, propertyId: string) => Promise<void>;
    onUnassign: (lock: AvailableUnifiSmartlock) => Promise<void>;
    isLoading: boolean;
}

function SmartlockCard({
                           lock,
                           properties,
                           onAssign,
                           onUnassign,
                           isLoading,
                       }: SmartlockCardProps) {
    const {t} = useTranslation();
    const [selectedPropertyId, setSelectedPropertyId] = useState<string | null>(null);
    const [isAssigning, setIsAssigning] = useState(false);
    const [isUnassigning, setIsUnassigning] = useState(false);
    const [unassignModalOpened, {open: openUnassignModal, close: closeUnassignModal}] =
        useDisclosure(false);

    const handleAssign = async () => {
        if (!selectedPropertyId) return;
        setIsAssigning(true);
        try {
            await onAssign(lock, selectedPropertyId);
            setSelectedPropertyId(null);
            showSuccessToast(t("unifiSmartlockCard.toast.assigned"), "🔐");
        } finally {
            setIsAssigning(false);
        }
    };

    const handleUnassignConfirm = async () => {
        setIsUnassigning(true);
        try {
            await onUnassign(lock);
            closeUnassignModal();
            showSuccessToast(t("unifiSmartlockCard.toast.unassigned"), "🔓");
        } finally {
            setIsUnassigning(false);
        }
    };

    const propertyOptions = properties.map((p) => ({
        value: p.id,
        label: p.name,
    }));

    const stateLabel = t(getLockStateKey(lock.lock_state));
    const stateColors = getLockStateColors(lock.lock_state);
    const lockOpen = isLockOpen(lock.lock_state);

    return (
        <Box
            style={{
                position: "relative",
                backgroundColor: "white",
                borderRadius: 24,
                padding: 32,
                border: "1px solid #F1F5F9",
                transition: "background-color 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease",
            }}
        >
            {/* Blue accent bar at top for Unifi */}
            <Box
                style={{
                    position: "absolute",
                    top: 0,
                    left: "50%",
                    transform: "translateX(-50%)",
                    width: 96,
                    height: 4,
                    backgroundColor: "#3B82F6",
                    borderRadius: "0 0 9999px 9999px",
                }}
            />

            {/* Header with icon and status */}
            <Group justify="space-between" align="flex-start" mb={24}>
                <Box
                    style={{
                        width: 56,
                        height: 56,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        backgroundColor: "#F8FAFC",
                        borderRadius: 16,
                    }}
                >
                    {lockOpen ? (
                        <IconLockOpen size={28} color="#334155" stroke={1.5}/>
                    ) : (
                        <IconLock size={28} color="#334155" stroke={1.5}/>
                    )}
                </Box>
                <Box
                    style={{
                        display: "inline-flex",
                        alignItems: "center",
                        padding: "4px 12px",
                        borderRadius: 9999,
                        backgroundColor: stateColors.bg,
                        color: stateColors.text,
                        fontSize: 12,
                        fontWeight: 600,
                    }}
                >
                    <Box
                        style={{
                            width: 6,
                            height: 6,
                            borderRadius: 9999,
                            backgroundColor: stateColors.dot,
                            marginRight: 8,
                        }}
                    />
                    {stateLabel}
                </Box>
            </Group>

            {/* Lock name and ID */}
            <Text fw={700} size="lg" mb={4}>
                {lock.name}
            </Text>
            <Text
                size="xs"
                c="#94A3B8"
                mb={20}
                style={{
                    fontFamily: "monospace",
                    textTransform: "uppercase",
                    letterSpacing: "0.1em",
                }}
            >
                ID: {lock.door_id}
            </Text>

            {/* Assignment section */}
            <Box
                pt={12}
                style={{
                    borderTop: "1px solid #F8FAFC",
                    display: "flex",
                    flexDirection: "column",
                    height: 156,
                }}
            >
                <Box style={{flex: 1}}>
                    <Text
                        size="xs"
                        fw={700}
                        c="#94A3B8"
                        mb={8}
                        style={{
                            textTransform: "uppercase",
                            letterSpacing: "0.15em",
                            fontSize: 10,
                        }}
                    >
                        {lock.assigned_property ? t("unifiSmartlockCard.assignedProperty") : t("unifiSmartlockCard.assignToProperty")}
                    </Text>
                    {lock.assigned_property ? (
                        <Group gap={8} style={{minHeight: 36, alignItems: "center"}}>
                            <IconHome size={14} color="#334155"/>
                            <Text size="sm" fw={500} c="#334155">
                                {lock.assigned_property.property_name}
                            </Text>
                        </Group>
                    ) : (
                        <Select
                            placeholder={t("unifiSmartlockCard.selectProperty")}
                            data={propertyOptions}
                            value={selectedPropertyId}
                            onChange={setSelectedPropertyId}
                            disabled={isLoading || properties.length === 0}
                            radius="xl"
                            styles={{
                                input: {
                                    backgroundColor: "#F8FAFC",
                                    border: "none",
                                    fontWeight: 500,
                                },
                            }}
                        />
                    )}
                </Box>
                <Box mt={12}>
                    {lock.assigned_property ? (
                        <Button
                            fullWidth
                            variant="outline"
                            color="red"
                            radius="xl"
                            onClick={openUnassignModal}
                            disabled={isLoading}
                            styles={{
                                root: {
                                    borderColor: "#FECACA",
                                    color: "#DC2626",
                                    fontWeight: 600,
                                    "&:hover": {
                                        backgroundColor: "#FEF2F2",
                                    },
                                },
                            }}
                        >
                            {t("unifiSmartlockCard.removeAssignment")}
                        </Button>
                    ) : (
                        <Button
                            fullWidth
                            color="dark"
                            radius="xl"
                            onClick={handleAssign}
                            loading={isAssigning}
                            disabled={!selectedPropertyId || isLoading}
                            styles={{
                                root: {
                                    fontWeight: 600,
                                },
                            }}
                        >
                            {t("unifiSmartlockCard.assignLock")}
                        </Button>
                    )}
                </Box>
            </Box>

            {/* Unassign confirmation modal */}
            <Modal
                opened={unassignModalOpened}
                onClose={closeUnassignModal}
                title={t("unifiSmartlockCard.unassignModal.title")}
                centered
            >
                <Text size="sm" mb="lg">
                    {t("unifiSmartlockCard.unassignModal.description")}
                </Text>
                <Group justify="flex-end">
                    <Button variant="subtle" onClick={closeUnassignModal}>
                        {t("unifiSmartlockCard.unassignModal.cancel")}
                    </Button>
                    <Button
                        color="red"
                        onClick={handleUnassignConfirm}
                        loading={isUnassigning}
                    >
                        {t("unifiSmartlockCard.unassignModal.confirm")}
                    </Button>
                </Group>
            </Modal>
        </Box>
    );
}

export default function UnifiIntegrationDetails() {
    const {id} = useParams();
    const navigate = useNavigate();
    const {t} = useTranslation();
    const [deleteModalOpened, {open: openDeleteModal, close: closeDeleteModal}] =
        useDisclosure(false);

    const [smartlocks, setSmartlocks] = useState<AvailableUnifiSmartlock[]>([]);
    const [properties, setProperties] = useState<Property[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isDeleting, setIsDeleting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [unifiStatus, setUnifiStatus] = useState<UnifiStatus>("online");

    // Credentials form state
    const [consoleUrl, setConsoleUrl] = useState("");
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    const loadData = async () => {
        setIsLoading(true);
        setError(null);
        setUnifiStatus("online");

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());

            const [smartlocksResponse, propertiesResponse, integrationsResponse] = await Promise.all([
                apiClient.getUnifiSmartlocks(id!),
                apiClient.getProperties(),
                apiClient.getUnifiIntegrations(),
            ]);

            const found = integrationsResponse.data.find((i) => i.id === id);
            if (found) {
                setConsoleUrl(found.unifi_console_url);
                setUsername(found.unifi_username);
            }
            setPassword("");

            setSmartlocks(smartlocksResponse.data.smartlocks);
            setUnifiStatus(smartlocksResponse.data.unifi_status);
            setProperties(propertiesResponse.data.properties);
        } catch (err: any) {
            console.error("Error loading data:", err);
            setError(t("unifiDetails.loadError"));
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleAssign = async (lock: AvailableUnifiSmartlock, propertyId: string) => {
        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.assignUnifiSmartlockToProperty(propertyId, {
                name: lock.name,
                unifi_floor_id: lock.floor_id,
                unifi_door_id: lock.door_id,
            });
            await loadData();
        } catch (err: any) {
            console.error("Error assigning lock:", err);
            if (err.status === 409) {
                setError(t("unifiDetails.assignConflictError"));
            } else {
                setError(t("unifiDetails.assignError"));
            }
            throw err;
        }
    };

    const handleUnassign = async (lock: AvailableUnifiSmartlock) => {
        if (!lock.assigned_property) return;

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.unassignSmartlockFromProperty(
                lock.assigned_property.property_id,
                lock.assigned_property.smartlock_record_id,
            );
            await loadData();
        } catch (err: any) {
            console.error("Error unassigning lock:", err);
            setError(t("unifiDetails.unassignError"));
            throw err;
        }
    };

    const handleSaveCredentials = async () => {
        setIsSaving(true);
        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.updateUnifiIntegration(id!, {
                unifi_console_url: consoleUrl.trim(),
                unifi_username: username.trim(),
                unifi_password: password,
            });
            setPassword("");
            showSuccessToast(t("unifiDetails.toast.updated"));
            await loadData();
        } catch (err) {
            console.error("Error updating integration:", err);
            const code = err instanceof ApiClientError ? err.code : null;
            const key = code ? `unifiDetails.error.${code}` : null;
            showErrorToast(key && t(key) !== key ? t(key) : t("unifiDetails.updateError"));
        } finally {
            setIsSaving(false);
        }
    };

    const handleDeleteIntegration = async () => {
        setIsDeleting(true);
        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            await apiClient.deleteUnifiIntegration(id!);
            closeDeleteModal();
            navigate("/integrations");
        } catch (err: any) {
            console.error("Error deleting integration:", err);
            setError(t("unifiDetails.deleteError"));
        } finally {
            setIsDeleting(false);
        }
    };

    const breadcrumbItems = [
        {title: t("nav.integrations"), href: "/integrations"},
        {title: t("unifiDetails.breadcrumb"), href: "#"},
    ];

    return (
        <Container fluid={true}>
            <ContentHeader
                breadcrumbs={breadcrumbItems}
                subtitle={t("unifiDetails.subtitle")}
                right={
                    <>
                        <SecondaryButton
                            color="red"
                            leftSection={<IconTrash size={16}/>}
                            onClick={openDeleteModal}
                            visibleFrom="sm"
                        >
                            {t("unifiDetails.removeButton")}
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

            {/* Error alert */}
            {error && (
                <ErrorMessage message={error}/>
            )}

            {/* Unifi connection problems (credentials rejected / console unreachable) */}
            {unifiStatus === "unauthorized" && (
                <ErrorMessage
                    title={t("unifiDetails.invalidCredentialsTitle")}
                    message={t("unifiDetails.invalidCredentialsMessage")}
                    mb="36px"
                />
            )}
            {unifiStatus === "unreachable" && (
                <ErrorMessage
                    title={t("unifiDetails.unreachableTitle")}
                    message={t("unifiDetails.unreachableMessage")}
                    mb="36px"
                />
            )}

            {/* Loading state */}
            {isLoading ? (
                <Text c="dimmed" size="sm">
                    {t("unifiDetails.loading")}
                </Text>
            ) : error || unifiStatus !== "online" ? null : smartlocks.length === 0 ? (
                <Text c="dimmed" size="sm">
                    {t("unifiDetails.emptyMessage")}
                </Text>
            ) : (
                /* Smart locks grid */
                <SimpleGrid cols={{base: 1, md: 2, xl: 6}} spacing={32}>
                    {smartlocks.map((lock) => (
                        <SmartlockCard
                            key={lock.door_id}
                            lock={lock}
                            properties={properties}
                            onAssign={handleAssign}
                            onUnassign={handleUnassign}
                            isLoading={isLoading}
                        />
                    ))}</SimpleGrid>
            )}

            {!isLoading && (
                <>
                    <Divider my={48}/>
                    <Stack gap={16}>
                        <Box>
                            <Title order={3} mb={4}>
                                {t("unifiDetails.credentials.title")}
                            </Title>
                            <Text size="sm" c="dimmed">
                                {t("unifiDetails.credentials.description")}
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
                                    label={t("unifiDetails.credentials.consoleUrlLabel")}
                                    description={t("unifiDetails.credentials.consoleUrlDescription")}
                                    value={consoleUrl}
                                    onChange={(e) => setConsoleUrl(e.currentTarget.value)}
                                    required
                                />
                                <TextInput
                                    label={t("unifiDetails.credentials.usernameLabel")}
                                    description={t("unifiDetails.credentials.usernameDescription")}
                                    value={username}
                                    onChange={(e) => setUsername(e.currentTarget.value)}
                                    required
                                />
                                <PasswordInput
                                    label={t("unifiDetails.credentials.passwordLabel")}
                                    description={t("unifiDetails.credentials.passwordDescription")}
                                    value={password}
                                    onChange={(e) => setPassword(e.currentTarget.value)}
                                    required
                                />
                                <Group justify="flex-start">
                                    <MainButton
                                        onClick={handleSaveCredentials}
                                        loading={isSaving}
                                        disabled={!consoleUrl.trim() || !username.trim() || !password.trim()}
                                    >
                                        {t("unifiDetails.credentials.save")}
                                    </MainButton>
                                </Group>
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
                                <IconTrash size={24} className="text-red-600"/>
                            </div>
                            <div>
                                <Text
                                    size="xl"
                                    fw={700}
                                    className="tracking-tight text-slate-900 dark:text-white"
                                >
                                    {t("unifiDetails.removeModal.title")}
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
                            {t("unifiDetails.removeModal.description")}
                        </Text>

                        <div className="bg-red-50 border border-red-200 rounded-xl p-4">
                            <Text size="sm" fw={600} c="red.7" mb={8}>
                                {t("unifiDetails.removeModal.warningHeader")}
                            </Text>
                            <ul className="text-sm text-red-700 space-y-1 ml-4 list-disc">
                                <li>{t("unifiDetails.removeModal.item1")}</li>
                                <li>{t("unifiDetails.removeModal.item2")}</li>
                                <li>{t("unifiDetails.removeModal.item3")}</li>
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
                                {t("unifiDetails.removeModal.cancel")}
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
                                {t("unifiDetails.removeModal.confirm")}
                            </Button>
                        </div>
                    </Stack>
                </Box>
            </Modal>
        </Container>
    );
}
