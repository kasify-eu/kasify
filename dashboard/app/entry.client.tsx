import { HydratedRouter } from "react-router/dom";
import { startTransition, StrictMode } from "react";
import { hydrateRoot } from "react-dom/client";
import i18n from "~/i18n";
import i18next from "i18next";
import { I18nextProvider, initReactI18next } from "react-i18next";
import LanguageDetector from "i18next-browser-languagedetector";
import Backend from "i18next-http-backend";

declare global {
  interface Window {
    __I18N__?: { locale: string; common: Record<string, unknown> };
  }
}

async function hydrate() {
  // The server inlined the active locale's namespace into a <script> in root.tsx.
  // Seeding init() with these resources means the very first client render has
  // translations available — no HTTP round-trip, no key flash on hydration.
  const bootstrap = typeof window !== "undefined" ? window.__I18N__ : undefined;
  const initialResources = bootstrap
    ? { [bootstrap.locale]: { common: bootstrap.common } }
    : undefined;

  await i18next
    .use(initReactI18next)
    .use(LanguageDetector)
    .use(Backend)
    .init({
      ...i18n,
      lng: bootstrap?.locale,
      ns: ["common"],
      resources: initialResources,
      // Tell i18next that other languages may not be in `resources` and the
      // backend should fetch them on demand (e.g. when the user clicks EN→ES).
      partialBundledLanguages: true,
      backend: { loadPath: "/locales/{{lng}}/{{ns}}.json" },
      detection: {
        order: ["htmlTag"],
        caches: [],
      },
    });

  startTransition(() => {
    hydrateRoot(
      document,
      <I18nextProvider i18n={i18next}>
        <StrictMode>
          <HydratedRouter />
        </StrictMode>
      </I18nextProvider>
    );
  });
}

hydrate().catch((error) => console.error(error));
