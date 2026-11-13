import {useEffect, useState} from "react";
import {Box, Button, Modal, Stack, Text, TextInput} from "@mantine/core";
import {IconAlertCircle, IconArrowRight, IconCircleCheck, IconTicket} from "@tabler/icons-react";
import {Trans, useTranslation} from "react-i18next";
import {ApiClient, ApiClientError, createApiConfig} from "~/lib/api-client";
import {useNavigate} from "react-router";

export type PropertyJoinInitialState =
    | {
          status: "success";
          propertyName: string;
          propertyId: string;
      }
    | {
          status: "error";
          code: string | null;
          message: string;
      };

type PropertyJoinModalProps = {
    opened: boolean;
    onClose: () => void;
    initialState?: PropertyJoinInitialState | null;
};

type ModalState = "input" | "success" | "error";

type JoinResult = {
    propertyName: string;
    propertyId: string;
};

function translateErrorCode(
    t: ReturnType<typeof useTranslation>["t"],
    code: string | null | undefined,
    fallback: string,
): string {
    if (code) {
        const key = `propertyJoinModal.error.${code}`;
        const translated = t(key);
        if (translated !== key) return translated;
    }
    return fallback || t("propertyJoinModal.joinError");
}

export function PropertyJoinModal({opened, onClose, initialState}: PropertyJoinModalProps) {
    const {t} = useTranslation();
    const [state, setState] = useState<ModalState>("input");
    const [code, setCode] = useState("");
    const [joinResult, setJoinResult] = useState<JoinResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    useEffect(() => {
        if (!opened || !initialState) return;
        if (initialState.status === "success") {
            setJoinResult({
                propertyName: initialState.propertyName,
                propertyId: initialState.propertyId,
            });
            setState("success");
            setError(null);
        } else {
            setError(translateErrorCode(t, initialState.code, initialState.message));
            setState("error");
            setJoinResult(null);
        }
    }, [opened, initialState, t]);

    const handleClose = () => {
        setState("input");
        setCode("");
        setJoinResult(null);
        setError(null);
        onClose();
    };

    const handleTryAgain = () => {
        setState("input");
        setCode("");
        setError(null);
    };

    const handleJoin = async () => {
        if (!code.trim()) {
            setError(t("propertyJoinModal.codeError"));
            return;
        }

        setLoading(true);
        setError(null);

        try {
            const apiClient = ApiClient.createFromBrowser(createApiConfig());
            const response = await apiClient.acceptInvitation(code.trim());
            setJoinResult({
                propertyName: response.data.propertyName,
                propertyId: response.data.propertyId,
            });
            setState("success");
        } catch (err) {
            const errorCode = err instanceof ApiClientError ? err.code : null;
            const fallback = err instanceof Error ? err.message : "";
            setError(translateErrorCode(t, errorCode, fallback));
        } finally {
            setLoading(false);
        }
    };

    const handleViewProperty = () => {
        if (joinResult) {
            handleClose();
            navigate(`/properties/${joinResult.propertyId}`);
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
            {state === "input" && (
                <Box>
                    {/* Header */}
                    <div className="p-8 pb-0 flex justify-between items-start">
                        <div>
                            <Text size="xl" fw={700} className="tracking-tight text-slate-900 ">
                                {t("propertyJoinModal.title")}
                            </Text>
                            <Text size="sm" c="dimmed" mt={4}>
                                {t("propertyJoinModal.subtitle")}
                            </Text>
                        </div>
                        <button
                            onClick={handleClose}
                            className="p-2 hover:bg-slate-100 rounded-full transition-colors"
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
                                    {t("propertyJoinModal.codeLabel")}
                                </label>
                                <TextInput
                                    placeholder={t("propertyJoinModal.codePlaceholder")}
                                    value={code}
                                    onChange={(e) => setCode(e.target.value.trim())}
                                    leftSection={<IconTicket size={18} className="text-slate-400"/>}
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
                                            fontFamily: "monospace",
                                            letterSpacing: "0.1em",
                                        },
                                    }}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") {
                                            handleJoin();
                                        }
                                    }}
                                />
                            </div>

                            <Button
                                fullWidth
                                radius={16}
                                onClick={handleJoin}
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
                                {t("propertyJoinModal.joinButton")}
                            </Button>

                            {error && (
                                <Text c="red" size="sm" ta="center">
                                    {error}
                                </Text>
                            )}

                            <div className="pt-2">
                                <Text
                                    size="xs"
                                    c="dimmed"
                                    ta="center"
                                    className="uppercase tracking-widest font-bold"
                                    style={{fontSize: 10}}
                                >
                                    {t("propertyJoinModal.howItWorksTitle")}
                                </Text>
                                <Text size="xs" c="dimmed" ta="center" mt={8} px={16}
                                      style={{fontSize: 11, lineHeight: 1.6}}>
                                    {t("propertyJoinModal.howItWorksBody")}
                                </Text>
                            </div>
                        </Stack>
                    </div>
                </Box>
            )}

            {state === "success" && (
                <Box className="p-8 pb-0 flex flex-col items-center text-center">
                    {/* Success Icon */}
                    <div
                        className="w-14 h-14 bg-emerald-100  rounded-full flex items-center justify-center mb-4">
                        <IconCircleCheck size={32} className="text-emerald-600 "/>
                    </div>

                    <Text size="xl" fw={700} mb={8}>
                        {t("propertyJoinModal.successTitle")}
                    </Text>
                    <Text c="dimmed" mb={20} size="sm">
                        <Trans
                            i18nKey="propertyJoinModal.successSubtitle"
                            values={{propertyName: joinResult?.propertyName}}
                            components={{bold: <Text span fw={600}/>}}
                        />
                    </Text>

                    {/* Actions */}
                    <div className="w-full space-y-3">
                        <Button
                            fullWidth
                            radius={16}
                            onClick={handleViewProperty}
                            styles={{
                                root: {
                                    backgroundColor: "#0F172A",
                                    height: 52,
                                    fontWeight: 700,
                                    fontSize: 14,
                                },
                            }}
                        >
                            {t("propertyJoinModal.viewProperty")}
                        </Button>
                    </div>

                    <button
                        onClick={handleClose}
                        className="mt-4 mb-6 text-slate-500 hover:text-slate-900  text-sm font-medium transition-colors"
                    >
                        {t("propertyJoinModal.done")}
                    </button>
                </Box>
            )}

            {state === "error" && (
                <Box className="p-8 pb-0 flex flex-col items-center text-center">
                    {/* Error Icon */}
                    <div
                        className="w-14 h-14 bg-red-100  rounded-full flex items-center justify-center mb-4">
                        <IconAlertCircle size={32} className="text-red-600 "/>
                    </div>

                    <Text size="xl" fw={700} mb={8}>
                        {t("propertyJoinModal.errorTitle")}
                    </Text>
                    <Text c="dimmed" mb={20} size="sm">
                        {error ?? t("propertyJoinModal.errorSubtitle")}
                    </Text>

                    {/* Actions */}
                    <div className="w-full space-y-3">
                        <Button
                            fullWidth
                            radius={16}
                            onClick={handleTryAgain}
                            styles={{
                                root: {
                                    backgroundColor: "#0F172A",
                                    height: 52,
                                    fontWeight: 700,
                                    fontSize: 14,
                                },
                            }}
                        >
                            {t("propertyJoinModal.tryAgain")}
                        </Button>
                    </div>

                    <button
                        onClick={handleClose}
                        className="mt-4 mb-6 text-slate-500 hover:text-slate-900  text-sm font-medium transition-colors"
                    >
                        {t("propertyJoinModal.close")}
                    </button>
                </Box>
            )}
        </Modal>
    );
}
