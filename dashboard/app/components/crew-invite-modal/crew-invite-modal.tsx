import {useState} from "react";
import {Box, Button, CopyButton, Modal, Select, Stack, Text, TextInput} from "@mantine/core";
import {IconArrowRight, IconCheck, IconCircleCheck, IconCopy, IconMail, IconShield} from "@tabler/icons-react";
import {useTranslation} from "react-i18next";
import {ApiClient, createApiConfig} from "~/lib/api-client";
import type {PropertyAccessLevel, PropertyInvitation} from "~/types/crew";

type CrewInviteModalProps = {
    opened: boolean;
    onClose: () => void;
    propertyId: string;
};

type ModalState = "input" | "success";

export function CrewInviteModal({opened, onClose, propertyId}: CrewInviteModalProps) {
    const {t, i18n} = useTranslation();
    const [state, setState] = useState<ModalState>("input");
    const [email, setEmail] = useState("");
    const [accessLevel, setAccessLevel] = useState<PropertyAccessLevel>("staff");
    const [invitation, setInvitation] = useState<PropertyInvitation | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleClose = () => {
        setState("input");
        setEmail("");
        setAccessLevel("contractor");
        setInvitation(null);
        setError(null);
        onClose();
    };

    const handleGenerateCode = async () => {
        setError(null);

        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!email || !emailRegex.test(email)) {
            setError(t("crewInviteModal.emailError"));
            return;
        }

        setLoading(true);

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            const locale = i18n.language.startsWith("es") ? "es" : "en";
            const response = await apiClient.createCrewInvitation(propertyId, {
                ...(email ? {email} : {}),
                accessLevel,
                locale,
            });
            setInvitation(response.data);
            setState("success");
        } catch (err) {
            setError(err instanceof Error ? err.message : t("crewInviteModal.generateError"));
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={handleClose}
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
            {state === "input" ? (
                <Box>
                    {/* Header */}
                    <div className="p-8 pb-0 flex justify-between items-start">
                        <div>
                            <Text size="xl" fw={700} className="tracking-tight text-slate-900 dark:text-white">
                                {t("crewInviteModal.title")}
                            </Text>
                            <Text size="sm" c="dimmed" mt={4}>
                                {t("crewInviteModal.subtitle")}
                            </Text>
                        </div>
                        <button
                            onClick={handleClose}
                            className="p-2 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-full transition-colors"
                        >
                            <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24"
                                 stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                                      d="M6 18L18 6M6 6l12 12"/>
                            </svg>
                        </button>
                    </div>

                    {/* Content */}
                    <div className="p-8">
                        <Stack gap="lg">
                            <div>
                                <label
                                    className="block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-3">
                                    {t("crewInviteModal.emailLabel")}
                                </label>
                                <TextInput
                                    placeholder={t("crewInviteModal.emailPlaceholder")}
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    leftSection={<IconMail size={18} className="text-slate-400"/>}
                                    size="lg"
                                    radius={16}
                                    styles={{
                                        input: {
                                            backgroundColor: "var(--mantine-color-gray-0)",
                                            border: "none",
                                            paddingLeft: 48,
                                            paddingRight: 16,
                                            paddingTop: 16,
                                            paddingBottom: 16,
                                            fontSize: 14,
                                        },
                                    }}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") {
                                            handleGenerateCode();
                                        }
                                    }}
                                />
                            </div>

                            <div>
                                <label
                                    className="block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-3">
                                    {t("crewInviteModal.roleLabel")}
                                </label>
                                <Select
                                    value={accessLevel}
                                    onChange={(v) => setAccessLevel((v ?? "contractor") as PropertyAccessLevel)}
                                    leftSection={<IconShield size={18} className="text-slate-400"/>}
                                    size="lg"
                                    radius={16}
                                    allowDeselect={false}
                                    data={[
                                        {value: "manager", label: t("crewInviteModal.role.manager")},
                                        {value: "staff", label: t("crewInviteModal.role.staff")},
                                        {value: "contractor", label: t("crewInviteModal.role.contractor")},
                                    ]}
                                    styles={{
                                        input: {
                                            backgroundColor: "var(--mantine-color-gray-0)",
                                            border: "none",
                                            paddingLeft: 48,
                                            paddingRight: 16,
                                            paddingTop: 16,
                                            paddingBottom: 16,
                                            fontSize: 14,
                                        },
                                        option: {
                                            fontSize: 13,
                                        },
                                    }}
                                />
                                <Text size="xs" c="dimmed" mt={10} px={4} style={{fontSize: 12, lineHeight: 1.6}}>
                                    {t(`crewInviteModal.roleInfo.${accessLevel}`)}
                                </Text>
                            </div>

                            <Button
                                fullWidth
                                radius={16}
                                onClick={handleGenerateCode}
                                loading={loading}
                                rightSection={!loading && <IconArrowRight size={16}
                                                                          className="group-hover:translate-x-1 transition-transform"/>}
                                styles={{
                                    root: {
                                        backgroundColor: "#0F172A",
                                        height: 52,
                                        fontWeight: 700,
                                        fontSize: 14,
                                    },
                                }}
                            >
                                {t("crewInviteModal.generateButton")}
                            </Button>

                            {error && (
                                <Text c="red" size="sm" ta="center">
                                    {error}
                                </Text>
                            )}

                            <div>
                                <Text size="xs" c="dimmed" ta="center" mt={4} px={16}
                                      style={{fontSize: 11, lineHeight: 1.6}}>
                                    {t("crewInviteModal.securityNoticeBody")}
                                </Text>
                            </div>
                        </Stack>
                    </div>
                </Box>
            ) : (
                <Box className="p-8 pb-0 flex flex-col items-center text-center">
                    {/* Success Icon */}
                    <div
                        className="w-14 h-14 bg-emerald-100  rounded-full flex items-center justify-center mb-4">
                        <IconCircleCheck size={32} className="text-emerald-600 "/>
                    </div>

                    <Text size="xl" fw={700} mb={8}>
                        {t("crewInviteModal.successTitle")}
                    </Text>
                    <Text c="dimmed" mb={20} size="sm">
                        {t("crewInviteModal.successSubtitle")}
                    </Text>

                    {/* Code Display */}
                    <div
                        className="w-full bg-slate-50 rounded-2xl p-2.5 mb-4 border border-slate-100 ">
                        <Text
                            size="md"
                            className="font-mono font-bold tracking-[0.2em] text-slate-900 dark:text-white"
                        >
                            {invitation?.token}
                        </Text>
                    </div>

                    {/* Actions */}
                    <div className="w-full space-y-3">
                        <CopyButton value={invitation?.token || ""}>
                            {({copied, copy}) => (
                                <Button
                                    fullWidth
                                    radius={16}
                                    onClick={copy}
                                    leftSection={copied ? <IconCheck size={18}/> : <IconCopy size={18}/>}
                                    styles={{
                                        root: {
                                            backgroundColor: "#0F172A",
                                            height: 52,
                                            fontWeight: 700,
                                            fontSize: 14,
                                        },
                                    }}
                                >
                                    {copied ? t("crewInviteModal.copiedButton") : t("crewInviteModal.copyButton")}
                                </Button>
                            )}
                        </CopyButton>

                        {email && (
                            <div
                                className="flex items-center justify-center gap-2 py-2 text-emerald-600  text-sm font-semibold">
                                <IconMail size={16}/>
                                <span>{t("crewInviteModal.sentTo", {email})}</span>
                            </div>
                        )}
                    </div>

                    <button
                        onClick={handleClose}
                        className="mt-4 mb-6 text-slate-500 hover:text-slate-900  text-sm font-medium transition-colors cursor-pointer"
                    >
                        {t("crewInviteModal.done")}
                    </button>
                </Box>
            )}
        </Modal>
    );
}
