import { useEffect, useMemo, useState } from "react";
import {
  ActionIcon,
  Badge,
  Card,
  Group,
  Stack,
  Text,
  Tooltip,
} from "@mantine/core";
import { IconKey, IconPlus, IconTrash } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { MainButton } from "~/components/main-button/main-button";
import type { ApiToken } from "~/types/api-tokens";
import { CreateTokenModal } from "./create-token-modal";
import { RevokeTokenModal } from "./revoke-token-modal";

type ApiTokensCardProps = {
  tokens: ApiToken[];
  onChanged: () => void;
};

export function ApiTokensCard({ tokens, onChanged }: ApiTokensCardProps) {
  const { t, i18n } = useTranslation();
  const [createOpen, setCreateOpen] = useState(false);
  const [revoking, setRevoking] = useState<ApiToken | null>(null);

  const dateFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(i18n.language, {
        year: "numeric",
        month: "short",
        day: "numeric",
      }),
    [i18n.language],
  );

  return (
    <Card withBorder p="xl" maw={900} radius="lg" mt="lg">
      <Stack gap="lg">
        <Group justify="space-between" align="flex-start">
          <div>
            <Text fw={600} size="lg">
              {t("account.apiTokens.title")}
            </Text>
            <Text size="sm" c="dimmed" mt={4}>
              {t("account.apiTokens.description")}
            </Text>
          </div>
          <MainButton
            leftSection={<IconPlus size={16} />}
            onClick={() => setCreateOpen(true)}
          >
            {t("account.apiTokens.newToken")}
          </MainButton>
        </Group>

        {tokens.length === 0 ? (
          <Text size="sm" c="dimmed" ta="center" py="xl">
            {t("account.apiTokens.empty")}
          </Text>
        ) : (
          <Stack gap="sm">
            {tokens.map((token) => (
              <TokenRow
                key={token.id}
                token={token}
                dateFormatter={dateFormatter}
                onRevokeClick={() => setRevoking(token)}
              />
            ))}
          </Stack>
        )}
      </Stack>

      <CreateTokenModal
        opened={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false);
          onChanged();
        }}
      />

      <RevokeTokenModal
        token={revoking}
        onClose={() => setRevoking(null)}
        onRevoked={() => {
          setRevoking(null);
          onChanged();
        }}
      />
    </Card>
  );
}

function TokenRow({
  token,
  dateFormatter,
  onRevokeClick,
}: {
  token: ApiToken;
  dateFormatter: Intl.DateTimeFormat;
  onRevokeClick: () => void;
}) {
  const { t } = useTranslation();

  const lastUsed = token.last_used_at
    ? t("account.apiTokens.lastUsed", {
        date: dateFormatter.format(new Date(token.last_used_at)),
      })
    : t("account.apiTokens.neverUsed");

  const expires = token.expires_at
    ? t("account.apiTokens.expiresOn", {
        date: dateFormatter.format(new Date(token.expires_at)),
      })
    : t("account.apiTokens.neverExpires");

  return (
    <Card withBorder p="md" radius="md">
      <Group justify="space-between" wrap="nowrap" align="flex-start">
        <Group align="flex-start" gap="md" wrap="nowrap">
          <IconKey size={20} className="text-slate-400 mt-1" />
          <Stack gap={2}>
            <Text fw={600}>{token.name}</Text>
            <Group gap="xs" wrap="wrap">
              <Badge variant="light" color="gray" radius="sm">
                {token.token_prefix}…
              </Badge>
              <Text size="xs" c="dimmed">
                {lastUsed}
              </Text>
              <Text size="xs" c="dimmed">
                · {expires}
              </Text>
            </Group>
          </Stack>
        </Group>
        <Tooltip label={t("account.apiTokens.revokeAction")} withArrow>
          <ActionIcon
            variant="subtle"
            color="red"
            aria-label={t("account.apiTokens.revokeAction")}
            onClick={onRevokeClick}
          >
            <IconTrash size={18} />
          </ActionIcon>
        </Tooltip>
      </Group>
    </Card>
  );
}
