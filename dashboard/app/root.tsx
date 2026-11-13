import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  useRouteLoaderData,
} from "react-router";
import {
  ColorSchemeScript,
  createTheme,
  mantineHtmlProps,
  MantineProvider,
} from "@mantine/core";
import { Toaster, ToastBar, toast } from "react-hot-toast";

import type { Route } from "./+types/root";
import i18nextServer, { getResourceBundle } from "~/i18next.server";
import "@mantine/core/styles.css";
import "@mantine/dates/styles.css";
import "./app.css";

const theme = createTheme({
  primaryColor: "gray",
  fontFamily: '"Inter", system-ui, sans-serif',
  components: {
    TextInput: {
      styles: {
        label: {
          fontSize: "0.75rem",
          fontWeight: 500,
          color: "#727272",
          margin: "0",
          paddingBottom: "0px",
        },
        input: {
          height: "44px",
          fontSize: "0.875rem",
          padding: "10px 12px",
        },
      },
    },
    Select: {
      styles: {
        label: {
          fontSize: "0.75rem",
          fontWeight: 500,
          color: "#727272",
          margin: "0",
          paddingBottom: "0px",
        },
        input: {
          height: "44px",
          fontSize: "0.875rem",
          padding: "10px 12px",
        },
      },
    },
    PasswordInput: {
      styles: {
        label: {
          fontSize: "0.75rem",
          fontWeight: 500,
          color: "#727272",
        },
        input: {
          height: "44px",
          fontSize: "0.875rem",
          padding: "10px 12px",
        },
      },
    },
    NumberInput: {
      styles: {
        label: {
          fontSize: "0.75rem",
          fontWeight: 500,
          color: "#727272",
          margin: "0",
          paddingBottom: "0px",
        },
        input: {
          height: "44px",
          fontSize: "0.875rem",
          padding: "10px 12px",
        },
      },
    },
    Textarea: {
      styles: {
        label: {
          fontSize: "0.75rem",
          fontWeight: 500,
          color: "#727272",
          margin: "0",
          paddingBottom: "0px",
        },
        input: {
          fontSize: "0.875rem",
          padding: "10px 12px",
        },
      },
    },
    DateInput: {
      styles: {
        label: {
          fontSize: "0.75rem",
          fontWeight: 500,
          color: "#727272",
          margin: "0",
          paddingBottom: "0px",
        },
        input: {
          height: "44px",
          fontSize: "0.875rem",
          padding: "10px 12px",
        },
      },
    },
  },
});

export const handle = { i18n: "common" };

export async function loader({ request }: Route.LoaderArgs) {
  const locale = await i18nextServer.getLocale(request);
  const resources = await getResourceBundle(locale, "common");
  const publicUrl = process.env.VITE_KASIFY_PUBLIC_URL ?? "";
  const docsUrl = process.env.VITE_KASIFY_DOCS_URL ?? "";
  const legalUrl = process.env.VITE_KASIFY_LEGAL_URL ?? "";
  const analyticsScriptUrl = process.env.ANALYTICS_SCRIPT_URL ?? "";
  return { locale, resources, publicUrl, docsUrl, legalUrl, analyticsScriptUrl };
}

export const links: Route.LinksFunction = () => [
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@500;600&display=swap",
  }
];

export function Layout({ children }: { children: React.ReactNode }) {
  const data = useRouteLoaderData<typeof loader>("root");
  const locale = data?.locale ?? "en";
  const resources = data?.resources;
  const publicUrl = data?.publicUrl ?? "";
  const docsUrl = data?.docsUrl ?? "";
  const legalUrl = data?.legalUrl ?? "";
  const analyticsScriptUrl = data?.analyticsScriptUrl ?? "";

  // Pre-seed i18next with the active locale's namespace so the very first
  // client render already has translations. Without this, the http-backend
  // would fetch the JSON asynchronously and we'd briefly render i18n keys
  // until the resource store fills in.
  // The </script> escape prevents an attacker-supplied translation value from
  // breaking out of the script tag.
  const i18nBootstrap = resources
    ? `window.__I18N__=${JSON.stringify({ locale, common: resources }).replace(/</g, "\\u003c")};`
    : "";

  const publicUrlBootstrap =
    `window.__KASIFY_PUBLIC_URL__=${JSON.stringify(publicUrl)};` +
    `window.__KASIFY_DOCS_URL__=${JSON.stringify(docsUrl)};` +
    `window.__KASIFY_LEGAL_URL__=${JSON.stringify(legalUrl)};`;
  const safePublicUrlBootstrap = publicUrlBootstrap.replace(/</g, "\\u003c");

  return (
    <html lang={locale} {...mantineHtmlProps}>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <link rel="icon" type="image/png" href="/favicon-96x96.png" sizes="96x96" />
        <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
        <link rel="shortcut icon" href="/favicon.ico" />
        <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png" />
        <meta name="apple-mobile-web-app-title" content="Kasify" />
        <link rel="manifest" href="/site.webmanifest" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="default" />
        <link rel="manifest" href="/manifest.json" />
        <meta name="theme-color" content="#ffffff" />
        <ColorSchemeScript
          defaultColorScheme="light"
          forceColorScheme="light"
        />
        {i18nBootstrap && (
          <script dangerouslySetInnerHTML={{ __html: i18nBootstrap }} />
        )}
        <script dangerouslySetInnerHTML={{ __html: safePublicUrlBootstrap }} />
        <Meta />
        <Links />
      </head>
      <body>
        <MantineProvider
          theme={theme}
          forceColorScheme="light"
          defaultColorScheme="light"
        >
          {children}
        </MantineProvider>
        <Toaster position="top-right">
          {(t) => (
            <div onClick={() => toast.dismiss(t.id)} style={{ cursor: "pointer" }}>
              <ToastBar toast={t} />
            </div>
          )}
        </Toaster>
        <ScrollRestoration />
        <Scripts />

        {analyticsScriptUrl && <script async src={analyticsScriptUrl}></script>}
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let message = "Oops!";
  let details = "An unexpected error occurred.";
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    message = error.status === 404 ? "404" : "Error";
    details =
      error.status === 404
        ? "The requested page could not be found."
        : error.statusText || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <main className="pt-16 p-4 container mx-auto">
      <h1>{message}</h1>
      <p>{details}</p>
      {stack && (
        <pre className="w-full p-4 overflow-x-auto">
          <code>{stack}</code>
        </pre>
      )}
    </main>
  );
}
