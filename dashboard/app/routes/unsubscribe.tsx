import { Box, Paper, Stack, Text, ThemeIcon, Title } from "@mantine/core";
import { IconCircleCheckFilled, IconMailOff } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useLoaderData } from "react-router";
import { ApiClient, createApiConfig } from "~/lib/api-client";
import type { Route } from "./+types/unsubscribe";

export function meta(_: Route.MetaArgs) {
  return [{ title: "Unsubscribe — Kasify" }];
}

// The unsubscribe itself runs in the loader so the emailed link works with a
// plain GET and without JavaScript. The API call is idempotent, so repeat
// visits (or email-client prefetches) are harmless.
export async function loader({ params, request }: Route.LoaderArgs) {
  try {
    const client = ApiClient.createPublic(request, createApiConfig());
    const response = await client.unsubscribeMarketing(params.token);
    return { emailMasked: response.data.email_masked, error: false };
  } catch {
    return { emailMasked: null, error: true };
  }
}

export default function Unsubscribe() {
  const { emailMasked, error } = useLoaderData<typeof loader>();
  const { t } = useTranslation();

  return (
    <Box
      mih="100vh"
      bg="gray.0"
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 16,
      }}
    >
      <Paper p="xl" radius="lg" withBorder maw={420} w="100%" bg="white">
        <Stack align="center" gap="md" py="sm">
          <ThemeIcon size={72} radius="xl" variant="light" color={error ? "gray" : "green"}>
            {error ? <IconMailOff size={40} /> : <IconCircleCheckFilled size={48} />}
          </ThemeIcon>
          <Title order={3} ta="center" style={{ letterSpacing: "-0.02em" }}>
            {error ? t("unsubscribe.errorTitle") : t("unsubscribe.title")}
          </Title>
          <Text ta="center" c="dimmed" size="sm">
            {error
              ? t("unsubscribe.errorBody")
              : t("unsubscribe.body", { email: emailMasked })}
          </Text>
        </Stack>
      </Paper>
    </Box>
  );
}
