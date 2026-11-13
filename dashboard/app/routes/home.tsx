import { Box, Button, Image, Stack, Text } from "@mantine/core";
import { useTranslation } from "react-i18next";
import { redirect } from "react-router";
import type { Route } from "./+types/home";
import { LanguageSelector } from "~/components/language-selector";
import { userContext } from "~/context";
import { authMiddleware } from "~/middleware/auth";

export const middleware = [authMiddleware];

// The marketing site is a separate project; this is the entry point of the app
// itself: signed-in visitors go straight to the timeline, everyone else gets a
// button that starts the SSO login.
export async function loader({ context }: Route.LoaderArgs) {
  const user = context.get(userContext);
  if (user) throw redirect("/timeline");
  return null;
}

export default function Home() {
  const { t } = useTranslation();

  return (
    <Box
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "#F8FAFC",
        padding: 16,
      }}
    >
      <Box
        style={{
          width: "100%",
          maxWidth: 400,
          padding: "clamp(32px, 6vw, 48px) clamp(24px, 5vw, 40px)",
          backgroundColor: "#FFFFFF",
          borderRadius: 16,
          border: "1px solid #E2E8F0",
        }}
      >
        <Stack align="center" gap="xl">
          <Image src="/logo.svg" h={28} w="auto" fit="contain" alt="Kasify" />

          <Text
            size="md"
            ta="center"
            style={{ color: "#64748B", lineHeight: 1.5 }}
          >
            {t("login.tagline")}
          </Text>

          <Button
            component="a"
            href="/kinde-auth/login"
            size="md"
            radius="lg"
            fullWidth
            styles={{
              root: {
                backgroundColor: "#121212",
                color: "#f1f1f1",
                fontWeight: 600,
                letterSpacing: "0.04em",
                WebkitFontSmoothing: "antialiased",
              },
            }}
          >
            {t("login.signIn")}
          </Button>

          <LanguageSelector />
        </Stack>
      </Box>
    </Box>
  );
}
