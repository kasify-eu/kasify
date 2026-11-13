import {Box, Button, Group, Modal, Select, Stack, Text} from "@mantine/core";
import {useDisclosure} from "@mantine/hooks";
import {useState} from "react";
import {useTranslation} from "react-i18next";
import {IconHome, IconLock, IconLockOpen, IconUnlink} from "@tabler/icons-react";
import type {AvailableNukiSmartlock} from "~/types/integrations";
import type {Property} from "~/types/properties";
import {showSuccessToast} from "~/lib/toast";

function getLockStateKey(lockState: number | undefined): string {
  if (lockState === undefined || lockState === null) return "nukiSmartlockCard.state.unknown";
  switch (lockState) {
    case 0:
      return "nukiSmartlockCard.state.uncalibrated";
    case 1:
      return "nukiSmartlockCard.state.locked";
    case 2:
      return "nukiSmartlockCard.state.unlocking";
    case 3:
      return "nukiSmartlockCard.state.unlocked";
    case 4:
      return "nukiSmartlockCard.state.locking";
    case 5:
      return "nukiSmartlockCard.state.unlatched";
    case 6:
      return "nukiSmartlockCard.state.unlocked";
    case 7:
      return "nukiSmartlockCard.state.unlatching";
    case 254:
      return "nukiSmartlockCard.state.motorBlocked";
    case 255:
      return "nukiSmartlockCard.state.unknown";
    default:
      return "nukiSmartlockCard.state.unknown";
  }
}

function getLockStateColors(lockState: number | undefined): { bg: string; text: string; dot: string } {
  if (lockState === undefined || lockState === null) {
    return { bg: "#F1F5F9", text: "#64748B", dot: "#94A3B8" };
  }
  switch (lockState) {
    case 1: // Locked
      return { bg: "#D1FAE5", text: "#047857", dot: "#10B981" };
    case 3: // Unlocked
    case 5: // Unlatched
    case 6: // Unlocked (Lock'n'Go)
      return { bg: "#FEF3C7", text: "#B45309", dot: "#F59E0B" };
    case 254: // Motor blocked
      return { bg: "#FEE2E2", text: "#DC2626", dot: "#EF4444" };
    default:
      return { bg: "#F1F5F9", text: "#64748B", dot: "#94A3B8" };
  }
}

function isLockOpen(lockState: number | undefined): boolean {
  if (lockState === undefined || lockState === null) return false;
  return lockState === 3 || lockState === 5 || lockState === 6;
}

interface NukiSmartlockCardProps {
  lock: AvailableNukiSmartlock;
  properties: Property[];
  onAssign: (lock: AvailableNukiSmartlock, propertyId: string) => Promise<void>;
  onUnassign: (lock: AvailableNukiSmartlock) => Promise<void>;
  isLoading: boolean;
}

export function NukiSmartlockCard({
  lock,
  properties,
  onAssign,
  onUnassign,
  isLoading,
}: NukiSmartlockCardProps) {
  const { t } = useTranslation();
  const [selectedPropertyId, setSelectedPropertyId] = useState<string | null>(null);
  const [isAssigning, setIsAssigning] = useState(false);
  const [isUnassigning, setIsUnassigning] = useState(false);
  const [unassignModalOpened, { open: openUnassignModal, close: closeUnassignModal }] =
    useDisclosure(false);

  const handleAssign = async () => {
    if (!selectedPropertyId) return;
    setIsAssigning(true);
    try {
      await onAssign(lock, selectedPropertyId);
      setSelectedPropertyId(null);
      showSuccessToast(t("nukiSmartlockCard.toast.assigned"), "🔐");
    } finally {
      setIsAssigning(false);
    }
  };

  const handleUnassignConfirm = async () => {
    setIsUnassigning(true);
    try {
      await onUnassign(lock);
      closeUnassignModal();
      showSuccessToast(t("nukiSmartlockCard.toast.unassigned"), "🔓");
    } finally {
      setIsUnassigning(false);
    }
  };

  const propertyOptions = properties.map((p) => ({
    value: p.id,
    label: p.name,
  }));

  const isOffline = lock.offline === true;
  const stateLabel = isOffline ? t("nukiSmartlockCard.state.offline") : t(getLockStateKey(lock.lock_state));
  const stateColors = isOffline
    ? { bg: "#F1F5F9", text: "#64748B", dot: "#94A3B8" }
    : getLockStateColors(lock.lock_state);
  const lockOpen = isOffline ? false : isLockOpen(lock.lock_state);

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
      {/* Yellow accent bar at top */}
      <Box
        style={{
          position: "absolute",
          top: 0,
          left: "50%",
          transform: "translateX(-50%)",
          width: 96,
          height: 4,
          backgroundColor: "#FACC15",
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
            <IconLockOpen size={28} color="#334155" stroke={1.5} />
          ) : (
            <IconLock size={28} color="#334155" stroke={1.5} />
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

      {/* Lock name and serial */}
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
        SN: {lock.smartlock_id}
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
        <Box style={{ flex: 1 }}>
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
            {lock.assigned_property ? t("nukiSmartlockCard.assignedProperty") : t("nukiSmartlockCard.assignToProperty")}
          </Text>
          {lock.assigned_property ? (
            <Group gap={8} style={{ minHeight: 36, alignItems: "center" }}>
              <IconHome size={14} color="#334155" />
              <Text size="sm" fw={500} c="#334155">
                {lock.assigned_property.property_name}
              </Text>
            </Group>
          ) : (
            <Select
              placeholder={t("nukiSmartlockCard.selectProperty")}
              data={propertyOptions}
              value={selectedPropertyId}
              onChange={setSelectedPropertyId}
              disabled={isLoading || properties.length === 0 || isOffline}
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
              {t("nukiSmartlockCard.removeAssignment")}
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
              {t("nukiSmartlockCard.assignLock")}
            </Button>
          )}
        </Box>
      </Box>

      {/* Unassign confirmation modal */}
      <Modal
        opened={unassignModalOpened}
        onClose={closeUnassignModal}
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
                <IconUnlink size={24} className="text-red-600" />
              </div>
              <div>
                <Text size="xl" fw={700} className="tracking-tight text-slate-900">
                  {t("nukiSmartlockCard.unassignModal.title")}
                </Text>
              </div>
            </div>
            <button
              onClick={closeUnassignModal}
              className="p-2 hover:bg-slate-100 rounded-full transition-colors"
            >
              <svg className="w-5 h-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <Stack gap="md">
            <Text size="sm" c="dimmed">
              {t("nukiSmartlockCard.unassignModal.description")}
            </Text>

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={closeUnassignModal}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("nukiSmartlockCard.unassignModal.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={isUnassigning}
                onClick={handleUnassignConfirm}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("nukiSmartlockCard.unassignModal.confirm")}
              </Button>
            </div>
          </Stack>
        </Box>
      </Modal>
    </Box>
  );
}
