import { useState } from "react";
import { Button, Group, Modal, Stack, Text } from "@mantine/core";
import { useTranslation } from "react-i18next";
import {
  ApiClient,
  ApiClientError,
  createApiConfig,
} from "~/lib/api-client";
import { showErrorToast, showSuccessToast } from "~/lib/toast";

type DisconnectTelegramModalProps = {
  opened: boolean;
  onClose: () => void;
  onDisconnected: () => void;
};

export function DisconnectTelegramModal({
  opened,
  onClose,
  onDisconnected,
}: DisconnectTelegramModalProps) {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);

  const handleConfirm = async () => {
    setLoading(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.unlinkTelegram();
      showSuccessToast(t("telegram.disconnectedToast"));
      onDisconnected();
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `telegram.error.${code}` : null;
      const translated =
        key && t(key) !== key ? t(key) : t("telegram.disconnectError");
      showErrorToast(translated);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (loading) return;
    onClose();
  };

  return (
    <Modal
      opened={opened}
      onClose={handleClose}
      title={t("telegram.disconnectTitle")}
      centered
      radius="lg"
    >
      <Stack gap="lg">
        <Text size="sm" c="dimmed">
          {t("telegram.disconnectBody")}
        </Text>
        <Group justify="flex-end" gap="sm">
          <Button
            variant="default"
            radius="md"
            onClick={handleClose}
            disabled={loading}
          >
            {t("common.cancel")}
          </Button>
          <Button
            color="red"
            radius="md"
            loading={loading}
            onClick={handleConfirm}
          >
            {t("telegram.disconnectConfirm")}
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
