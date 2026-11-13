import type { ZudokuConfig } from "zudoku";
import { LandingRedirect } from "./src/LandingRedirect.js";
import { LanguageSwitcher } from "./src/LanguageSwitcher.js";

const SITE_ORIGIN = "https://docs.kasify.eu";

const config: ZudokuConfig = {
  // Absolute origin used for <link rel="canonical"> on every page. Also the
  // base for the generated sitemap and the hreflang alternates injected by
  // scripts/seo-postprocess.mjs.
  canonicalUrlOrigin: SITE_ORIGIN,
  sitemap: {
    siteUrl: SITE_ORIGIN,
  },
  site: {
    showPoweredBy: false,
    title: "Kasify",
    logo: {
      src: {
        light: "/logo-light.svg",
        dark: "/logo-dark.svg",
      },
      alt: "Kasify",
      width: 98,
    },
  },
  metadata: {
    title: "%s - Kasify",
    favicon: "/favicon.ico",
  },
  docs: {
    defaultOptions: {
      toc: true,
    },
  },
  search: {
    type: "pagefind",
    maxSubResults: 3,
  },
  slots: {
    "head-navigation-end": ({ location, navigate }) => (
      <LanguageSwitcher location={location} navigate={navigate} />
    ),
  },
  navigation: [
    {
      type: "category",
      label: "Documentation",
      items: [
        {
          type: "category",
          label: "Getting Started",
          icon: "sparkles",
          items: ["/en/introduction", "/en/getting-started"],
        },
        {
          type: "category",
          label: "Setup",
          icon: "settings",
          items: [
            {
              type: "category",
              label: "SES Hospedajes",
              items: [
                "/en/setup/ses-hospedajes/registration",
                "/en/setup/ses-hospedajes/configuration",
                "/en/setup/ses-hospedajes/errors",
                "/en/setup/ses-hospedajes/faq",
              ],
            },
            {
              type: "category",
              label: "Sync bookings",
              items: ["/en/setup/sync/airbnb", "/en/setup/sync/booking"],
            },
            "/en/setup/smart-locks",
            "/en/setup/tasks",
          ],
        },
        {
          type: "category",
          label: "Team and permissions",
          icon: "users",
          items: ["/en/team/invitations", "/en/roles"],
        },
      ],
    },
    {
      type: "category",
      label: "Documentación",
      items: [
        {
          type: "category",
          label: "Empezando",
          icon: "sparkles",
          items: ["/es/introduction", "/es/getting-started"],
        },
        {
          type: "category",
          label: "Configuración",
          icon: "settings",
          items: [
            {
              type: "category",
              label: "SES Hospedajes",
              items: [
                "/es/setup/ses-hospedajes/registration",
                "/es/setup/ses-hospedajes/configuration",
                "/es/setup/ses-hospedajes/errors",
                "/es/setup/ses-hospedajes/faq",
              ],
            },
            {
              type: "category",
              label: "Sincronizar reservas",
              items: ["/es/setup/sync/airbnb", "/es/setup/sync/booking"],
            },
            "/es/setup/smart-locks",
            "/es/setup/tasks",
          ],
        },
        {
          type: "category",
          label: "Equipo y permisos",
          icon: "users",
          items: ["/es/team/invitations", "/es/roles"],
        },
      ],
    },
    {
      type: "category",
      label: "Developers",
      items: [
        "/developers/getting-started",
        {
          type: "link",
          icon: "folder-cog",
          badge: {
            label: "New",
            color: "purple",
          },
          label: "API Reference",
          to: "/api",
        },
      ],
    },
    {
      type: "link",
      to: "/api",
      label: "API Reference",
    },
    {
      // Landing page at `/` — picks a locale based on localStorage / browser
      // preference and redirects to `/<locale>/introduction`. No label, so it
      // doesn't appear in the top nav.
      type: "custom-page",
      path: "/",
      element: <LandingRedirect />,
    },
  ],
  redirects: [
    { from: "/en", to: "/en/introduction" },
    { from: "/es", to: "/es/introduction" },
    { from: "/introduction", to: "/en/introduction" },
    // SES pages moved into the setup/ses-hospedajes/ folder (2026-07).
    {
      from: "/en/setup/ses-hospedajes-registration",
      to: "/en/setup/ses-hospedajes/registration",
    },
    {
      from: "/en/setup/ses-hospedajes",
      to: "/en/setup/ses-hospedajes/configuration",
    },
    {
      from: "/en/setup/ses-hospedajes-errors",
      to: "/en/setup/ses-hospedajes/errors",
    },
    {
      from: "/es/setup/ses-hospedajes-registration",
      to: "/es/setup/ses-hospedajes/registration",
    },
    {
      from: "/es/setup/ses-hospedajes",
      to: "/es/setup/ses-hospedajes/configuration",
    },
    {
      from: "/es/setup/ses-hospedajes-errors",
      to: "/es/setup/ses-hospedajes/errors",
    },
  ],
  apis: [
    {
      type: "file",
      input: "./apis/openapi.json",
      path: "/api",
    },
  ],
};

export default config;
