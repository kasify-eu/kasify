export type LocaleCode = "en" | "es";

export const LOCALES: ReadonlyArray<{ code: LocaleCode; label: string }> = [
  { code: "en", label: "English" },
  { code: "es", label: "Español" },
];

export const DEFAULT_LOCALE: LocaleCode = "en";

// URL scheme: every locale lives under a `/<locale>/` prefix
// (e.g. `/en/introduction`, `/es/introduction`).
//
// Keys are neutral base paths (without any locale prefix). Values list
// locales that have a page at `/<locale><basePath>`. Used by the switcher
// to disable locales that would 404.
export const TRANSLATED_PAGES: Readonly<
  Record<string, ReadonlyArray<LocaleCode>>
> = {
  "/introduction": ["en", "es"],
  "/getting-started": ["en", "es"],
  "/roles": ["en", "es"],
  "/setup/smart-locks": ["en", "es"],
  "/setup/ses-hospedajes/registration": ["en", "es"],
  "/setup/ses-hospedajes/configuration": ["en", "es"],
  "/setup/ses-hospedajes/errors": ["en", "es"],
  "/setup/ses-hospedajes/faq": ["en", "es"],
  "/setup/sync/airbnb": ["en", "es"],
  "/setup/sync/booking": ["en", "es"],
  "/setup/tasks": ["en", "es"],
  "/team/invitations": ["en", "es"],
};

const LOCALE_CODES = LOCALES.map((l) => l.code);
const LOCALE_PREFIX_RE = new RegExp(`^/(${LOCALE_CODES.join("|")})(?=/|$)`);

export function getBasePathAndLocale(pathname: string): {
  basePath: string;
  locale: LocaleCode;
} {
  const match = pathname.match(LOCALE_PREFIX_RE);
  if (match) {
    const stripped = pathname.slice(match[0].length);
    return {
      basePath: stripped === "" ? "/" : stripped,
      locale: match[1] as LocaleCode,
    };
  }
  return { basePath: pathname, locale: DEFAULT_LOCALE };
}

export function pathForLocale(
  basePath: string,
  locale: LocaleCode,
): string | null {
  const available = TRANSLATED_PAGES[basePath] ?? [];
  if (!available.includes(locale)) return null;
  return `/${locale}${basePath}`;
}

// Persisted locale preference. Set when the user explicitly picks a locale
// via the LanguageSwitcher; read at the landing page to restore their choice.
export const LOCALE_STORAGE_KEY = "kasify-docs-locale";

export function savePreferredLocale(locale: LocaleCode): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // localStorage can be blocked (private mode, disabled cookies). Ignore.
  }
}

export function detectPreferredLocale(): LocaleCode {
  if (typeof window === "undefined") return DEFAULT_LOCALE;

  try {
    const saved = localStorage.getItem(LOCALE_STORAGE_KEY);
    if (saved && LOCALE_CODES.includes(saved as LocaleCode)) {
      return saved as LocaleCode;
    }
  } catch {
    // ignore
  }

  const browserLangs = navigator.languages?.length
    ? navigator.languages
    : [navigator.language];
  for (const lang of browserLangs) {
    const primary = lang.split("-")[0]?.toLowerCase();
    if (primary && LOCALE_CODES.includes(primary as LocaleCode)) {
      return primary as LocaleCode;
    }
  }

  return DEFAULT_LOCALE;
}
