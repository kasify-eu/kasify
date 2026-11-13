import { redirect } from "react-router";
import type { ActionFunctionArgs } from "react-router";

// Only redirect back to the Referer when it points at our own origin; a
// cross-site POST with a crafted Referer would otherwise be an open redirect.
function safeReferer(referer: string | null, requestUrl: string): string {
  if (!referer) return "/timeline";
  try {
    const refererUrl = new URL(referer);
    if (refererUrl.origin === new URL(requestUrl).origin) {
      return refererUrl.pathname + refererUrl.search;
    }
  } catch {
    // Malformed Referer — fall through to the default.
  }
  return "/timeline";
}

export async function action({ request }: ActionFunctionArgs) {
  const formData = await request.formData();
  const locale = formData.get("locale") as string;
  const target = safeReferer(request.headers.get("Referer"), request.url);
  const valid = ["en", "es"].includes(locale) ? locale : "en";
  const secure = process.env.NODE_ENV === "production" ? " Secure;" : "";
  return redirect(target, {
    headers: {
      "Set-Cookie": `kasify_locale=${valid}; Path=/; Max-Age=31536000; SameSite=Lax;${secure}`,
    },
  });
}
