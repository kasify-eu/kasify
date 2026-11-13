import { Anchor, Group } from "@mantine/core";
import { useFetcher } from "react-router";
import { useTranslation } from "react-i18next";

export function LanguageSelector() {
  const { i18n } = useTranslation();
  const fetcher = useFetcher();

  const handleSelect = (value: string) => {
    i18n.changeLanguage(value);
    fetcher.submit({ locale: value }, { method: "post", action: "/api/set-locale" });
  };

  return (
    <Group gap="xs" justify="center" mt="lg">
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
