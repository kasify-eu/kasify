import Backend from "i18next-fs-backend";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { RemixI18Next } from "remix-i18next/server";
import i18n from "~/i18n";

const LOCALE_COOKIE_NAME = "kasify_locale";

// Cache parsed namespace JSON in memory so repeated SSR requests don't re-read
// from disk. The files are static (rebuild = new process), so a simple Map is fine.
const resourceCache = new Map<string, Record<string, unknown>>();

// Try the dev location (./public) first, then the prod build output. The Docker
// image now copies ./public, so the first path wins everywhere — but we keep
// the fallback so an SSR request never throws if the layout shifts.
const RESOURCE_PATH_CANDIDATES = ["./public/locales", "./build/client/locales"] as const;

export async function getResourceBundle(
  locale: string,
  namespace: string,
): Promise<Record<string, unknown> | null> {
  const key = `${locale}:${namespace}`;
  const cached = resourceCache.get(key);
  if (cached) return cached;
  for (const base of RESOURCE_PATH_CANDIDATES) {
    try {
      const filePath = resolve(`${base}/${locale}/${namespace}.json`);
      const parsed = JSON.parse(await readFile(filePath, "utf-8")) as Record<string, unknown>;
      resourceCache.set(key, parsed);
      return parsed;
    } catch {
      // Try the next candidate
    }
  }
  console.warn(`[i18n] could not locate ${locale}/${namespace}.json on disk`);
  return null;
}

const i18next = new RemixI18Next({
  detection: {
    supportedLanguages: i18n.supportedLngs,
    fallbackLanguage: i18n.fallbackLng,
    async findLocale(request: Request) {
      // 1. Cookie
      const cookies = Object.fromEntries(
        (request.headers.get("Cookie") ?? "").split(";").map((c) => {
          const [k, ...v] = c.trim().split("=");
          return [k.trim(), decodeURIComponent(v.join("="))];
        })
      );
      if (i18n.supportedLngs.includes(cookies[LOCALE_COOKIE_NAME])) {
        return cookies[LOCALE_COOKIE_NAME];
      }
      // 2. Accept-Language header
      const browserLang = (request.headers.get("Accept-Language") ?? "")
        .split(",")[0]
        .split("-")[0]
        .trim();
      if (i18n.supportedLngs.includes(browserLang)) return browserLang;
      // 3. Default
      return i18n.fallbackLng;
    },
  },
  i18next: {
    ...i18n,
    backend: { loadPath: resolve("./public/locales/{{lng}}/{{ns}}.json") },
  },
  plugins: [Backend],
});

export default i18next;
