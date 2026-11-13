declare global {
  interface Window {
    __KASIFY_PUBLIC_URL__?: string;
    __KASIFY_DOCS_URL__?: string;
    __KASIFY_LEGAL_URL__?: string;
  }
}

const DEFAULT_DOCS_URL = "https://docs.kasify.eu";

// Public URL of this dashboard, used to build links shared with guests and crew.
export function getPublicAppUrl(): string {
  if (typeof window === "undefined") {
    return process.env.VITE_KASIFY_PUBLIC_URL ?? "";
  }
  return window.__KASIFY_PUBLIC_URL__ ?? "";
}

// User documentation site (no trailing slash).
export function getDocsUrl(): string {
  const url =
    typeof window === "undefined"
      ? process.env.VITE_KASIFY_DOCS_URL
      : window.__KASIFY_DOCS_URL__;
  return (url || DEFAULT_DOCS_URL).replace(/\/$/, "");
}

// Base URL hosting the legal pages (/privacy-policy, ...). Empty means same origin.
export function getLegalUrl(): string {
  const url =
    typeof window === "undefined"
      ? process.env.VITE_KASIFY_LEGAL_URL
      : window.__KASIFY_LEGAL_URL__;
  return (url ?? "").replace(/\/$/, "");
}
