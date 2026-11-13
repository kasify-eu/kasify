import type { LoaderFunctionArgs, ActionFunctionArgs } from "react-router";
import { getDedupedKindeSession } from "~/lib/kinde-session";

// Backend paths that are public (no authentication required). Matched as
// prefixes against the full splat path, which includes the version segment
// (e.g. "v1/geo/countries", "v1/checkin/ABC123/guests"). Anonymous guests hit
// these from the browser via the same /api/v1 base, so the proxy must forward
// them without demanding a Kinde token. The backend still enforces its own
// authorization, so this only relaxes the proxy's token gate.
const PUBLIC_API_PREFIXES = ["v1/geo/", "v1/checkin/"];

function isPublicApiPath(apiPath: string | undefined): boolean {
  if (!apiPath) return false;
  return PUBLIC_API_PREFIXES.some((prefix) => apiPath.startsWith(prefix));
}

// Proxy all /api/* requests to the backend API
export async function loader({ request, params }: LoaderFunctionArgs) {
  const apiPath = params["*"];
  // Deduped: parallel /api/* requests with an expired access token would
  // otherwise each redeem the same single-use refresh token, invalidating
  // the session for all but one of them.
  const session = await getDedupedKindeSession(request);
  const token = await session.getToken();
  const isPublic = isPublicApiPath(apiPath);

  // The session auto-refreshes the access token when expired. Forward
  // session.headers on the response so the browser picks up the rotated
  // access_token / refresh_token cookies — otherwise the refresh is lost
  // in memory and the next request fails authentication.
  const sessionCookieHeaders = session.headers;

  if (!token && !isPublic) {
    console.warn(
      `[api-proxy] No token for ${request.method} /${apiPath} — Kinde refresh likely failed. Responding 401.`,
    );
    return new Response(
      JSON.stringify({ code: "unauthorized", message: "Session expired" }),
      {
        status: 401,
        headers: mergeHeaders(
          new Headers({ "content-type": "application/json" }),
          sessionCookieHeaders,
        ),
      },
    );
  }

  const url = new URL(request.url);

  // Construct the backend API URL
  const apiUrl = `${process.env.VITE_KASIFY_API_URL || "http://localhost:8080"}/${apiPath}${url.search}`;

  const forwardHeaders = new Headers(request.headers);
  // Public paths may have no token (anonymous guests). Only set Authorization
  // when we actually have one; otherwise strip any inbound value so we don't
  // forward a stale/partial credential to the backend.
  if (token) {
    forwardHeaders.set("Authorization", `Bearer ${token}`);
  } else {
    forwardHeaders.delete("Authorization");
  }

  // Preserve the original client context for the API. The browser → LB →
  // dashboard chain has already populated X-Forwarded-For with the real
  // client IP (copied above via `new Headers(request.headers)`); we just
  // ensure Host and Proto reflect the *original* request before we rewrite
  // Host to the API's host below. The API installs XForwardedHeaders and
  // reads call.request.origin.remoteHost from these.
  forwardHeaders.set("X-Forwarded-Host", url.host);
  forwardHeaders.set("X-Forwarded-Proto", url.protocol.replace(":", ""));

  forwardHeaders.set("Host", new URL(apiUrl).host);
  // Let fetch recalculate content-length from the actual body
  forwardHeaders.delete("content-length");

  const response = await fetch(apiUrl, {
    method: request.method,
    headers: forwardHeaders,
    body: request.method !== "GET" ? request.body : undefined,
    // @ts-expect-error Node fetch supports duplex for streaming bodies
    duplex: "half",
  });

  // Node.js fetch (undici) auto-decompresses the response body but keeps the
  // Content-Encoding header, so the browser would try to decompress an already
  // decompressed body and fail with ERR_CONTENT_DECODING_FAILED.
  // Strip the encoding-related headers to match the actual (decoded) body.
  const responseHeaders = new Headers(response.headers);
  responseHeaders.delete("content-encoding");
  responseHeaders.delete("transfer-encoding");

  // Append any refreshed Kinde cookies so the browser updates its session.
  for (const [key, value] of sessionCookieHeaders.entries()) {
    responseHeaders.append(key, value);
  }

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  });
}

export async function action({ request, params }: ActionFunctionArgs) {
  return loader({ request, params } as LoaderFunctionArgs);
}

function mergeHeaders(base: Headers, extra: Headers): Headers {
  for (const [key, value] of extra.entries()) {
    base.append(key, value);
  }
  return base;
}
