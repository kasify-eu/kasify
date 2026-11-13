import { Anchor, Box, Container, Group, Text } from "@mantine/core";
import { useTranslation } from "react-i18next";
import { Outlet, useFetcher } from "react-router";

function InlineLanguageSelector() {
  const { i18n } = useTranslation();
  const fetcher = useFetcher();

  const handleSelect = (value: string) => {
    i18n.changeLanguage(value);
    fetcher.submit(
      { locale: value },
      { method: "post", action: "/api/set-locale" },
    );
  };

  return (
    <Group gap="xs" justify="center">
      <Anchor
        size="xs"
        fw={i18n.language === "en" ? 700 : 400}
        c={i18n.language === "en" ? "dark" : "dimmed"}
        onClick={() => handleSelect("en")}
      >
        english
      </Anchor>
      <Anchor
        size="xs"
        fw={i18n.language === "es" ? 700 : 400}
        c={i18n.language === "es" ? "dark" : "dimmed"}
        onClick={() => handleSelect("es")}
      >
        español
      </Anchor>
    </Group>
  );
}

export default function CheckinLayout() {
  const { t } = useTranslation();

  return (
    <Box bg="gray.0" mih="100vh" style={{ display: "flex", flexDirection: "column" }}>
      <Container
        size="sm"
        p={0}
        style={{ flex: 1, display: "flex", flexDirection: "column" }}
      >
        <Box style={{ flex: 1, display: "flex", flexDirection: "column" }}>
          <Outlet />
        </Box>

        {/* Language Selector */}
        <Box mt="sm" mb="xs">
          <InlineLanguageSelector />
        </Box>

        {/* Footer */}
        <Text ta="center" size="xs" c="dimmed" py="lg">
          {t("checkin.copyright")}
        </Text>
      </Container>
    </Box>
  );
}
