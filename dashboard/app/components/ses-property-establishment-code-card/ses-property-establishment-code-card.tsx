import {Box, Group, Stack, Text, TextInput} from "@mantine/core";
import {useState} from "react";
import {useTranslation} from "react-i18next";
import {IconBuildingCommunity} from "@tabler/icons-react";
import type {Property} from "~/types/properties";
import {MainButton} from "~/components/main-button/main-button";

interface SesPropertyEstablishmentCodeCardProps {
    property: Property;
    onSave: (propertyId: string, code: string) => Promise<void>;
    // True when the property is configured on ANOTHER integration: the card is shown greyed-out
    // and read-only ("visible but locked") — the API would reject the write with
    // ses_hospedajes.property_already_configured anyway. Clear its code on the owning
    // integration's page first to move it (two-step reassignment).
    locked?: boolean;
}

export function SesPropertyEstablishmentCodeCard({
    property,
    onSave,
    locked = false,
}: SesPropertyEstablishmentCodeCardProps) {
    const {t} = useTranslation();
    const savedCode = property.ses_establishment_code ?? "";
    const [code, setCode] = useState<string>(savedCode);
    const [isSaving, setIsSaving] = useState(false);

    const trimmed = code.trim();
    const hasSavedCode = savedCode.length > 0;
    const isUnchanged = trimmed === savedCode;
    // When SES flagged the code (config error), re-saving the SAME value is a valid action: the
    // save clears the pause optimistically so pending submissions retry (the owner may have fixed
    // the code on the SES side, or verified it is correct after a transient mismatch).
    const isDisabled =
        trimmed.length === 0 || (isUnchanged && !property.ses_config_error_message);

    const handleSave = async () => {
        if (isDisabled) return;
        setIsSaving(true);
        try {
            await onSave(property.id, trimmed);
        } finally {
            setIsSaving(false);
        }
    };

    const addressLine = [property.address, property.city, property.zip_code]
        .filter((part): part is string => Boolean(part && part.trim()))
        .join(", ");

    return (
        <Box
            style={{
                position: "relative",
                backgroundColor: "white",
                borderRadius: 24,
                padding: 32,
                border: "1px solid #F1F5F9",
                opacity: locked ? 0.6 : 1,
            }}
        >
            <Box
                style={{
                    position: "absolute",
                    top: 0,
                    left: "50%",
                    transform: "translateX(-50%)",
                    width: 96,
                    height: 4,
                    backgroundColor: "#EF4444",
                    borderRadius: "0 0 9999px 9999px",
                }}
            />

            <Group align="flex-start" gap={16} mb={8} wrap="nowrap">
                <Box
                    style={{
                        width: 56,
                        height: 56,
                        flexShrink: 0,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        backgroundColor: "#F8FAFC",
                        borderRadius: 16,
                    }}
                >
                    <IconBuildingCommunity size={28} color="#334155" stroke={1.5}/>
                </Box>
                <Box style={{minWidth: 0, flex: 1}}>
                    <Text fw={700} size="lg" lineClamp={1} style={{minHeight: 28}}>
                        {property.name}
                    </Text>
                    <Text
                        size="xs"
                        c="#94A3B8"
                        lineClamp={2}
                        mt={4}
                        style={{minHeight: 36, overflow: "hidden"}}
                    >
                        {addressLine}
                    </Text>
                </Box>
            </Group>

            <Stack gap={12} pt={12} style={{borderTop: "1px solid #F8FAFC"}}>
                {property.ses_config_error_message && !locked && (
                    <Box
                        style={{
                            backgroundColor: "#FEF2F2",
                            border: "1px solid #FECACA",
                            borderRadius: 12,
                            padding: "12px 16px",
                        }}
                    >
                        <Text size="sm" fw={600} c="red.7" mb={4}>
                            {t("sesEstablishmentCodeCard.configError.title")}
                        </Text>
                        <Text size="xs" c="red.7" mb={4}>
                            {property.ses_config_error_message}
                        </Text>
                        <Text size="xs" c="red.7">
                            {t("sesEstablishmentCodeCard.configError.description")}
                        </Text>
                    </Box>
                )}
                <TextInput
                    label={t("sesEstablishmentCodeCard.inputLabel")}
                    description={t("sesEstablishmentCodeCard.inputDescription")}
                    placeholder={t("sesEstablishmentCodeCard.inputPlaceholder")}
                    value={code}
                    onChange={(e) => setCode(e.currentTarget.value)}
                    maxLength={10}
                    radius="md"
                    disabled={locked}
                />
                {locked ? (
                    <Text size="xs" c="#94A3B8">
                        {t("sesEstablishmentCodeCard.configuredElsewhere")}
                    </Text>
                ) : (
                    <MainButton
                        fullWidth
                        onClick={handleSave}
                        loading={isSaving}
                        disabled={isDisabled}
                    >
                        {hasSavedCode
                            ? t("sesEstablishmentCodeCard.updateButton")
                            : t("sesEstablishmentCodeCard.saveButton")}
                    </MainButton>
                )}
            </Stack>
        </Box>
    );
}
