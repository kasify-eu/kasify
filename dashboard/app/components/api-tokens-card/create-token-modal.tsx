import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Code,
  CopyButton,
  Modal,
  Select,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import {
  IconAlertTriangle,
  IconCheck,
  IconCircleCheck,
  IconCopy,
  IconKey,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import type { CreateApiTokenResponse } from "~/types/api-tokens";

type CreateTokenModalProps = {
  opened: boolean;
  onClose: () => void;
  onCreated: () => void;
};

const NEVER_EXPIRES = "never";

export function CreateTokenModal({
  opened,
  onClose,
  onCreated,
}: CreateTokenModalProps) {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [expiration, setExpiration] = useState<string>("365");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<CreateApiTokenResponse | null>(null);

  const reset = () => {
    setName("");
    setExpiration("365");
    setError(null);
    setLoading(false);
    setCreated(null);
  };

  const handleClose = () => {
    if (created) {
      onCreated();
    }
    reset();
    onClose();
  };

  const handleSubmit = async () => {
    setError(null);
    const trimmed = name.trim();
    if (!trimmed) {
      setError(t("account.apiTokens.error.api_token.invalid_name"));
      return;
    }
    setLoading(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      const response = await apiClient.createApiToken({
        name: trimmed,
        expires_in_days:
          expiration === NEVER_EXPIRES ? null : parseInt(expiration, 10),
      });
      setCreated(response.data);
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `account.apiTokens.error.${code}` : null;
      const translated =
        key && t(key) !== key ? t(key) : t("account.apiTokens.createError");
      setError(translated);
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
      <Box className="p-8">
        <div className="flex justify-between items-start mb-6">
          <div className="flex items-center gap-3">
            <div
              className={
                created
                  ? "w-12 h-12 bg-emerald-100 rounded-full flex items-center justify-center"
                  : "w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center"
              }
            >
              {created ? (
                <IconCircleCheck size={24} className="text-emerald-600" />
              ) : (
                <IconKey size={24} className="text-slate-700" />
              )}
            </div>
            <Text size="xl" fw={700} className="tracking-tight text-slate-900">
              {created
                ? t("account.apiTokens.createdTitle")
                : t("account.apiTokens.createTitle")}
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

        {created ? (
          <Stack gap="md">
            <Alert
              color="yellow"
              icon={<IconAlertTriangle size={18} />}
              radius="md"
            >
              {t("account.apiTokens.copyWarning")}
            </Alert>

            <Code block className="break-all text-sm">
              {created.token}
            </Code>

            <CopyButton value={created.token}>
              {({ copied, copy }) => (
                <Button
                  fullWidth
                  radius={16}
                  onClick={copy}
                  leftSection={
                    copied ? <IconCheck size={18} /> : <IconCopy size={18} />
                  }
                  styles={{
                    root: {
                      backgroundColor: copied ? undefined : "#0F172A",
                      height: 52,
                      fontWeight: 700,
                      fontSize: 14,
                    },
                  }}
                  variant={copied ? "light" : "filled"}
                  color={copied ? "teal" : undefined}
                >
                  {copied
                    ? t("account.apiTokens.copied")
                    : t("account.apiTokens.copyToken")}
                </Button>
              )}
            </CopyButton>

            <button
              onClick={handleClose}
              className="text-slate-500 hover:text-slate-900 text-sm font-medium transition-colors cursor-pointer mt-2"
            >
              {t("account.apiTokens.done")}
            </button>
          </Stack>
        ) : (
          <Stack gap="md">
            <div>
              <label className="block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-3">
                {t("account.apiTokens.nameLabel")}
              </label>
              <TextInput
                placeholder={t("account.apiTokens.namePlaceholder")}
                value={name}
                maxLength={80}
                onChange={(e) => setName(e.target.value)}
                size="lg"
                radius={16}
                data-autofocus
                styles={{
                  input: {
                    backgroundColor: "var(--mantine-color-gray-0)",
                    border: "none",
                    paddingLeft: 16,
                    paddingRight: 16,
                    paddingTop: 16,
                    paddingBottom: 16,
                    fontSize: 14,
                  },
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    handleSubmit();
                  }
                }}
              />
            </div>

            <div>
              <label className="block text-[10px] font-bold uppercase tracking-widest text-slate-400 mb-3">
                {t("account.apiTokens.expirationLabel")}
              </label>
              <Select
                value={expiration}
                onChange={(value) => setExpiration(value ?? "365")}
                allowDeselect={false}
                size="lg"
                radius={16}
                data={[
                  { value: "30", label: t("account.apiTokens.expiration.30d") },
                  { value: "90", label: t("account.apiTokens.expiration.90d") },
                  {
                    value: "180",
                    label: t("account.apiTokens.expiration.180d"),
                  },
                  {
                    value: "365",
                    label: t("account.apiTokens.expiration.365d"),
                  },
                  {
                    value: NEVER_EXPIRES,
                    label: t("account.apiTokens.expiration.never"),
                  },
                ]}
                styles={{
                  input: {
                    backgroundColor: "var(--mantine-color-gray-0)",
                    border: "none",
                    paddingLeft: 16,
                    paddingRight: 16,
                    paddingTop: 16,
                    paddingBottom: 16,
                    fontSize: 14,
                  },
                  option: { fontSize: 13 },
                }}
              />
            </div>

            {error && (
              <Text c="red" size="sm">
                {error}
              </Text>
            )}

            <div className="flex gap-3 mt-2">
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
                fullWidth
                radius={16}
                onClick={handleSubmit}
                loading={loading}
                styles={{
                  root: {
                    backgroundColor: "#0F172A",
                    height: 48,
                    fontWeight: 600,
                  },
                }}
              >
                {t("account.apiTokens.create")}
              </Button>
            </div>
          </Stack>
        )}
      </Box>
    </Modal>
  );
}
