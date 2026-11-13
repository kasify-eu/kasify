import {Box, Group, PasswordInput, Stack, Text, TextInput} from "@mantine/core";
import type {ReactNode} from "react";
import {useTranslation} from "react-i18next";

interface SesHospedajesCredentialsCardProps {
    title?: string;
    arrendadorCode: string;
    onArrendadorCodeChange: (value: string) => void;
    arrendadorCodePlaceholder?: string;
    sesUsername: string;
    onSesUsernameChange: (value: string) => void;
    usernamePlaceholder?: string;
    sesPassword: string;
    onSesPasswordChange: (value: string) => void;
    passwordDescription: string;
    passwordPlaceholder: string;
    footer: ReactNode;
}

export function SesHospedajesCredentialsCard({
    title,
    arrendadorCode,
    onArrendadorCodeChange,
    arrendadorCodePlaceholder,
    sesUsername,
    onSesUsernameChange,
    usernamePlaceholder,
    sesPassword,
    onSesPasswordChange,
    passwordDescription,
    passwordPlaceholder,
    footer,
}: SesHospedajesCredentialsCardProps) {
    const {t} = useTranslation();

    return (
        <Box
            style={{
                backgroundColor: "white",
                borderRadius: 16,
                padding: 32,
                maxWidth: 920,
            }}
        >
            <Stack gap="lg">
                {title && (
                    <Text fw={600} size="lg">
                        {title}
                    </Text>
                )}

                <TextInput
                    label={t("sesHospedajesNew.arrendadorCode.label")}
                    description={t("sesHospedajesNew.arrendadorCode.description")}
                    placeholder={arrendadorCodePlaceholder}
                    value={arrendadorCode}
                    onChange={(e) => onArrendadorCodeChange(e.currentTarget.value)}
                    maxLength={10}
                    required
                />

                <TextInput
                    label={t("sesHospedajesNew.username.label")}
                    description={t("sesHospedajesNew.username.description")}
                    placeholder={usernamePlaceholder}
                    value={sesUsername}
                    onChange={(e) => onSesUsernameChange(e.currentTarget.value)}
                    required
                />

                <PasswordInput
                    label={t("sesHospedajesNew.password.label")}
                    description={passwordDescription}
                    placeholder={passwordPlaceholder}
                    value={sesPassword}
                    onChange={(e) => onSesPasswordChange(e.currentTarget.value)}
                    required
                />

                <Group justify="flex-start" mt="xl">
                    {footer}
                </Group>
            </Stack>
        </Box>
    );
}
