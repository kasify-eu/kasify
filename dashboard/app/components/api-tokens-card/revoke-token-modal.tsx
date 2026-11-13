import { useState } from "react";
import { Box, Button, Modal, Stack, Text } from "@mantine/core";
import { IconTrash } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import type { ApiToken } from "~/types/api-tokens";

type RevokeTokenModalProps = {
  token: ApiToken | null;
  onClose: () => void;
  onRevoked: () => void;
};

export function RevokeTokenModal({
  token,
  onClose,
  onRevoked,
}: RevokeTokenModalProps) {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleConfirm = async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.revokeApiToken(token.id);
      showSuccessToast(
        t("account.apiTokens.revokeSuccess", { name: token.name }),
        "🗝️",
      );
      onRevoked();
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `account.apiTokens.error.${code}` : null;
      const translated =
        key && t(key) !== key ? t(key) : t("account.apiTokens.revokeError");
      setError(translated);
      showErrorToast(translated);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (loading) return;
    setError(null);
    onClose();
  };

  return (
    <Modal
      opened={token !== null}
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
      {token && (
        <Box className="p-8">
          <div className="flex justify-between items-start mb-6">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 bg-red-100 rounded-full flex items-center justify-center">
                <IconTrash size={24} className="text-red-600" />
              </div>
              <Text size="xl" fw={700} className="tracking-tight text-slate-900">
                {t("account.apiTokens.revokeTitle")}
              </Text>
            </div>
            <button
              onClick={handleClose}
              className="p-2 hover:bg-slate-100 rounded-full transition-colors"
              aria-label={t("common.cancel")}
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
            <Text size="sm" className="text-slate-900">
              {t("account.apiTokens.revokeConfirm", {
                name: token.name,
                prefix: token.token_prefix,
              })}
            </Text>
            <Text size="sm" c="dimmed">
              {t("account.apiTokens.revokeWarning")}
            </Text>

            {error && (
              <Text c="red" size="sm">
                {error}
              </Text>
            )}

            <div className="flex gap-3 mt-4">
              <Button
                variant="light"
                color="gray"
                fullWidth
                radius={16}
                onClick={handleClose}
                disabled={loading}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("common.cancel")}
              </Button>
              <Button
                color="red"
                fullWidth
                radius={16}
                loading={loading}
                onClick={handleConfirm}
                styles={{ root: { height: 48, fontWeight: 600 } }}
              >
                {t("account.apiTokens.revokeAction")}
              </Button>
            </div>
          </Stack>
        </Box>
      )}
    </Modal>
  );
}
