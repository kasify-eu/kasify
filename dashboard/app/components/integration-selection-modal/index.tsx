import { Modal, Stack, Group, Text, Box, UnstyledButton } from "@mantine/core";
import { IconBuilding, IconChevronRight, IconLock } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

type IntegrationOption = {
  id: string;
  name: string;
  descriptionKey: string;
  icon: React.ReactNode;
};

const ICON_PROPS = {
  size: 20,
  stroke: 1.75,
  className: "text-slate-500",
} as const;

const integrationOptions: IntegrationOption[] = [
  {
    id: "nuki",
    name: "Nuki",
    descriptionKey: "integrations.modal.nuki.description",
    icon: <IconLock {...ICON_PROPS} />,
  },
  {
    id: "unifi",
    name: "Unifi Access",
    descriptionKey: "integrations.modal.unifi.description",
    icon: <IconLock {...ICON_PROPS} />,
  },
  {
    id: "ses-hospedajes",
    name: "SES Hospedajes",
    descriptionKey: "integrations.modal.sesHospedajes.description",
    icon: <IconBuilding {...ICON_PROPS} />,
  },
];

type IntegrationSelectionModalProps = {
  opened: boolean;
  onClose: () => void;
  onSelect: (integrationId: string) => void;
};

export function IntegrationSelectionModal({
  opened,
  onClose,
  onSelect,
}: IntegrationSelectionModalProps) {
  const { t } = useTranslation();
  return (
    <Modal
      opened={opened}
      onClose={onClose}
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
      <Box>
        {/* Header */}
        <div className="p-8 pb-0 flex justify-between items-start">
          <div>
            <Text size="xl" fw={700} className="tracking-tight text-slate-900">
              {t("integrations.add")}
            </Text>
            <Text size="sm" c="dimmed" mt={4}>
              {t("integrations.modal.subtitle")}
            </Text>
          </div>
          <button
            type="button"
            aria-label={t("common.close")}
            onClick={onClose}
            className="p-2 hover:bg-slate-100 rounded-full transition-colors"
          >
            <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="p-8">
          <label className="block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-3">
            {t("integrations.modal.availableLabel")}
          </label>
          <Stack gap="sm">
            {integrationOptions.map((integration) => (
              <UnstyledButton
                key={integration.id}
                onClick={() => onSelect(integration.id)}
                className="group"
                style={{
                  padding: "16px 20px",
                  borderRadius: 16,
                  backgroundColor: "var(--mantine-color-gray-0)",
                  transition: "background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease",
                }}
              >
                <Group justify="space-between" wrap="nowrap">
                  <Group gap="md" wrap="nowrap">
                    <Box
                      style={{
                        flex: "0 0 40px",
                        width: 40,
                        height: 40,
                        padding: 10,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        borderRadius: 12,
                        backgroundColor: "white",
                        border: "1px solid var(--mantine-color-gray-2)",
                      }}
                    >
                      {integration.icon}
                    </Box>
                    <div>
                      <Text size="sm" fw={600}>
                        {integration.name}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {t(integration.descriptionKey)}
                      </Text>
                    </div>
                  </Group>
                  <IconChevronRight size={18} className="text-slate-300 group-hover:text-slate-500 transition-colors" />
                </Group>
              </UnstyledButton>
            ))}
          </Stack>
        </div>
      </Box>
    </Modal>
  );
}
