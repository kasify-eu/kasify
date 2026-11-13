import { useEffect, useMemo, useRef, useState } from "react";
import {
  Badge,
  Box,
  Button,
  Card,
  CopyButton,
  Group,
  Loader,
  Stack,
  Text,
} from "@mantine/core";
import {
  IconBrandTelegram,
  IconCheck,
  IconCopy,
  IconExternalLink,
} from "@tabler/icons-react";
import QRCode from "react-qr-code";
import { useTranslation } from "react-i18next";
import { MainButton } from "~/components/main-button/main-button";
import {
  ApiClient,
  ApiClientError,
  createApiConfig,
} from "~/lib/api-client";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import type {
  TelegramLinkResponse,
  TelegramStatusResponse,
} from "~/types/telegram";
import { DisconnectTelegramModal } from "./disconnect-telegram-modal";

type TelegramCardProps = {
  status: TelegramStatusResponse;
  onChanged: () => void;
};

const POLL_INTERVAL_MS = 3000;
const POLL_TIMEOUT_MS = 2 * 60 * 1000;

export function TelegramCard({ status, onChanged }: TelegramCardProps) {
  const { t, i18n } = useTranslation();
  // The active linking attempt (deep link + QR). Null means we're not mid-link.
  const [linkData, setLinkData] = useState<TelegramLinkResponse | null>(null);
  const [connecting, setConnecting] = useState(false);
  const [timedOut, setTimedOut] = useState(false);
  const [disconnectOpen, setDisconnectOpen] = useState(false);

  const isLinked = status.status === "linked";

  const dateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, {
        year: "numeric",
        month: "short",
        day: "numeric",
      }),
    [i18n.language],
  );

  // While a link attempt is active, poll the status endpoint until the webhook
  // marks the user linked (or we time out). Cleans up on unmount / cancel.
  const onChangedRef = useRef(onChanged);
  onChangedRef.current = onChanged;
  useEffect(() => {
    if (!linkData) return;
    let cancelled = false;
    const startedAt = Date.now();

    const interval = setInterval(async () => {
      if (Date.now() - startedAt > POLL_TIMEOUT_MS) {
        clearInterval(interval);
        if (!cancelled) setTimedOut(true);
        return;
      }
      try {
        const apiClient = ApiClient.createFromBrowser(createApiConfig());
        const res = await apiClient.getTelegramStatus();
        if (!cancelled && res.data.status === "linked") {
          clearInterval(interval);
          setLinkData(null);
          showSuccessToast(t("telegram.connectedToast"), "✅");
          onChangedRef.current();
        }
      } catch {
        // Transient poll failure — keep trying until timeout.
      }
    }, POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [linkData, t]);

  const handleConnect = async () => {
    setConnecting(true);
    setTimedOut(false);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      const res = await apiClient.startTelegramLink();
      // No auto-open: the user scans the QR (most common) or clicks "Open Telegram"
      // explicitly. Auto-opening stole focus from the QR and tripped popup blockers.
      setLinkData(res.data);
    } catch (err) {
      const code = err instanceof ApiClientError ? err.code : null;
      const key = code ? `telegram.error.${code}` : null;
      const translated =
        key && t(key) !== key ? t(key) : t("telegram.connectError");
      showErrorToast(translated);
    } finally {
      setConnecting(false);
    }
  };

  const cancelLinking = () => {
    setLinkData(null);
    setTimedOut(false);
  };

  return (
    <Card withBorder p="xl" maw={900} radius="lg" mt="lg">
      <Stack gap="lg">
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Group align="flex-start" gap="md" wrap="nowrap">
            <IconBrandTelegram size={22} className="text-sky-500 mt-1" />
            <div>
              <Text fw={600} size="lg">
                {t("telegram.title")}
              </Text>
              <Text size="sm" c="dimmed" mt={4}>
                {t("telegram.description")}
              </Text>
            </div>
          </Group>
          {isLinked && (
            <Badge color="teal" variant="light" radius="sm" size="lg">
              {t("telegram.connectedTitle")}
            </Badge>
          )}
        </Group>

        {isLinked ? (
          <LinkedView
            username={status.telegram_username ?? null}
            linkedAt={status.linked_at ?? null}
            dateFormatter={dateFormatter}
            onDisconnectClick={() => setDisconnectOpen(true)}
          />
        ) : linkData ? (
          <WaitingView
            deepLink={linkData.deep_link}
            timedOut={timedOut}
            onCancel={cancelLinking}
            onRetry={handleConnect}
          />
        ) : (
          <Group>
            <MainButton
              leftSection={<IconBrandTelegram size={16} />}
              loading={connecting}
              onClick={handleConnect}
            >
              {t("telegram.connect")}
            </MainButton>
          </Group>
        )}
      </Stack>

      <DisconnectTelegramModal
        opened={disconnectOpen}
        onClose={() => setDisconnectOpen(false)}
        onDisconnected={() => {
          setDisconnectOpen(false);
          onChanged();
        }}
      />
    </Card>
  );
}

function LinkedView({
  username,
  linkedAt,
  dateFormatter,
  onDisconnectClick,
}: {
  username: string | null;
  linkedAt: string | null;
  dateFormatter: Intl.DateTimeFormat;
  onDisconnectClick: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Group justify="space-between" align="center" wrap="wrap">
      <Stack gap={2}>
        <Text fw={600}>
          {username
            ? t("telegram.connectedAs", { username })
            : t("telegram.connectedTitle")}
        </Text>
        {linkedAt && (
          <Text size="xs" c="dimmed">
            {t("telegram.connectedSince", {
              date: dateFormatter.format(new Date(linkedAt)),
            })}
          </Text>
        )}
      </Stack>
      <Button
        variant="light"
        color="red"
        radius="md"
        onClick={onDisconnectClick}
      >
        {t("telegram.disconnect")}
      </Button>
    </Group>
  );
}

function WaitingView({
  deepLink,
  timedOut,
  onCancel,
  onRetry,
}: {
  deepLink: string;
  timedOut: boolean;
  onCancel: () => void;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Group align="flex-start" gap="xl" wrap="wrap">
      <Stack gap="xs" align="center">
        <Box
          p="md"
          bg="white"
          style={{ borderRadius: 16, border: "1px solid var(--mantine-color-gray-3)" }}
        >
          <QRCode value={deepLink} size={170} />
        </Box>
        <Text size="xs" c="dimmed" ta="center" maw={170}>
          {t("telegram.scanWithPhone")}
        </Text>
      </Stack>

      <Stack gap="sm" style={{ flex: 1, minWidth: 260 }}>
        {timedOut ? (
          <Text size="sm" c="dimmed">
            {t("telegram.timedOut")}
          </Text>
        ) : (
          <Group gap="xs">
            <Loader size="xs" />
            <Text fw={600}>{t("telegram.waitingTitle")}</Text>
          </Group>
        )}
        <Text size="sm" c="dimmed">
          {t("telegram.waitingBody")}
        </Text>

        <MainButton
          variant="primary"
          leftSection={<IconExternalLink size={16} />}
          onClick={() =>
            window.open(deepLink, "_blank", "noopener,noreferrer")
          }
        >
          {t("telegram.openOnThisDevice")}
        </MainButton>

        <Group gap="sm" mt="xs">
          <CopyButton value={deepLink}>
            {({ copied, copy }) => (
              <Button
                variant="subtle"
                color="gray"
                size="sm"
                radius="md"
                leftSection={
                  copied ? <IconCheck size={16} /> : <IconCopy size={16} />
                }
                onClick={copy}
              >
                {copied ? t("telegram.copied") : t("telegram.copyLink")}
              </Button>
            )}
          </CopyButton>
          {timedOut && (
            <Button variant="subtle" size="sm" radius="md" onClick={onRetry}>
              {t("telegram.tryAgain")}
            </Button>
          )}
          <Button
            variant="subtle"
            color="gray"
            size="sm"
            radius="md"
            onClick={onCancel}
          >
            {t("common.cancel")}
          </Button>
        </Group>
      </Stack>
    </Group>
  );
}
