import {
    Box,
    Button,
    Group, Image,
    Stack,
    Text,
    TextInput,
} from "@mantine/core";
import { authMiddleware } from "~/middleware/auth";
import { useState } from "react";
import { useNavigate, useLoaderData } from "react-router";
import { getDedupedKindeSession } from "~/lib/kinde-session";
import type { Route } from "./+types/signup";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import { IconStack2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export const middleware = [authMiddleware];

export async function loader({ request }: Route.LoaderArgs) {
  const { getUser } = await getDedupedKindeSession(request);
  const user = await getUser();

  return {
    givenName: user?.given_name || "",
    familyName: user?.family_name || "",
  };
}

export default function Signup() {
  const navigate = useNavigate();
  const { givenName, familyName } = useLoaderData<typeof loader>();
  const { t } = useTranslation();

  const [name, setName] = useState(givenName);
  const [lastName, setLastName] = useState(familyName);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    setLoading(true);
    setError(null);

    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.signup({
        name,
        last_name: lastName,
      });

      navigate("/refresh");
    } catch (err) {
      const emailTaken =
        err instanceof ApiClientError &&
        err.code === "signup.email_already_registered";
      setError(t(emailTaken ? "signup.emailAlreadyRegistered" : "signup.error"));
      setLoading(false);
    }
  };

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
          <Group gap="sm">
              <Image src="/logo.svg" h={28} />
          </Group>

          <Stack gap={4} align="center">
            <Text
              fw={600}
              size="lg"
              style={{ color: "#0F172A", letterSpacing: "-0.01em" }}
            >
              {t("signup.heading")}
            </Text>
            <Text
              size="md"
              ta="center"
              style={{ color: "#64748B", lineHeight: 1.5 }}
            >
              {t("signup.subtitle")}
            </Text>
          </Stack>

          {error && (
            <Box
              style={{
                width: "100%",
                padding: "12px 16px",
                backgroundColor: "#FEF2F2",
                border: "1px solid #FECACA",
                borderRadius: 8,
                color: "#DC2626",
                fontSize: "var(--mantine-font-size-sm)",
                fontWeight: 500,
              }}
            >
              {error}
            </Box>
          )}

          <Stack gap="sm" w="100%">
            <TextInput
              size="md"
              radius="lg"
              label={t("signup.firstName")}
              placeholder={t("signup.firstNamePlaceholder")}
              value={name}
              onChange={(e) => setName(e.currentTarget.value)}
              styles={{
                label: {
                  fontSize: "var(--mantine-font-size-sm)",
                  color: "#64748B",
                  paddingBottom: 6,
                  fontWeight: 500,
                },
              }}
            />

            <TextInput
              size="md"
              radius="lg"
              label={t("signup.lastName")}
              placeholder={t("signup.lastNamePlaceholder")}
              value={lastName}
              onChange={(e) => setLastName(e.currentTarget.value)}
              styles={{
                label: {
                  fontSize: "var(--mantine-font-size-sm)",
                  color: "#64748B",
                  paddingBottom: 6,
                  fontWeight: 500,
                },
              }}
            />

            <Button
              size="md"
              radius="lg"
              mt="sm"
              fullWidth
              onClick={handleSubmit}
              loading={loading}
              disabled={!name || !lastName}
              styles={{
                root: {
                  backgroundColor: "#121212",
                  color: "#f1f1f1",
                  fontWeight: 600,
                  letterSpacing: "0.04em",
                  transition: "background-color 0.2s ease",
                  WebkitFontSmoothing: "antialiased",
                },
                label: {
                  WebkitFontSmoothing: "antialiased",
                },
              }}
              onMouseEnter={(e: React.MouseEvent<HTMLButtonElement>) => {
                if (!loading && name && lastName)
                  e.currentTarget.style.backgroundColor = "#323232";
              }}
              onMouseLeave={(e: React.MouseEvent<HTMLButtonElement>) => {
                e.currentTarget.style.backgroundColor = "#121212";
              }}
            >
              {t("signup.submit")}
            </Button>
          </Stack>
        </Stack>
      </Box>
    </Box>
  );
}
