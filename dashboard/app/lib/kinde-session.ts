import { getKindeSession } from "@kinde-oss/kinde-remix-sdk";

type KindeSession = Awaited<ReturnType<typeof getKindeSession>>;

// Kinde refresh tokens are single-use (rotation). getKindeSession() refreshes
// an expired access token eagerly, so two concurrent requests — or two
// getKindeSession() calls within one request — each try to redeem the same
// refresh token, and the loser ends up with a dead session, bouncing the user
// to login. We therefore dedupe session creation per refresh-token cookie:
// every request carrying the same refresh token shares one session, and one
// refresh.
//
// Entries live for a short TTL after settling because browser requests fired
// before the rotated Set-Cookie landed still carry the old refresh token;
// they must reuse the rotated result rather than redeem the consumed token.
//
// The cache is per-process. Multiple dashboard instances behind a load
// balancer without sticky sessions can still race across instances.
const CACHE_TTL_MS = 10_000;

const sessionsByRefreshToken = new Map<
  string,
  { promise: Promise<KindeSession>; expiresAt: number }
>();

// The Kinde SDK stores the refresh token as a plain cookie, chunked as
// refresh_token, refresh_token1, refresh_token2, ... when it exceeds the
// per-cookie size limit. The joined value is only used as a cache key, so it
// is never decoded or validated here.
function refreshTokenFromRequest(request: Request): string | null {
  const cookieHeader = request.headers.get("Cookie");
  if (!cookieHeader) return null;

  const cookies = new Map<string, string>();
  for (const part of cookieHeader.split(";")) {
    const eq = part.indexOf("=");
    if (eq === -1) continue;
    cookies.set(part.slice(0, eq).trim(), part.slice(eq + 1).trim());
  }

  const chunks: string[] = [];
  for (let i = 0; i < 20; i++) {
    const value = cookies.get(i === 0 ? "refresh_token" : `refresh_token${i}`);
    if (value === undefined) break;
    chunks.push(value);
  }
  return chunks.length > 0 ? chunks.join("") : null;
}

export async function getDedupedKindeSession(
  request: Request,
): Promise<KindeSession> {
  const key = refreshTokenFromRequest(request);
  if (!key) {
    // No refresh token in the request — nothing can rotate, no race to avoid.
    return getKindeSession(request);
  }

  const now = Date.now();
  for (const [k, entry] of sessionsByRefreshToken) {
    if (entry.expiresAt <= now) sessionsByRefreshToken.delete(k);
  }

  const cached = sessionsByRefreshToken.get(key);
  if (cached) return cached.promise;

  const promise = getKindeSession(request);
  sessionsByRefreshToken.set(key, { promise, expiresAt: now + CACHE_TTL_MS });
  // The SDK swallows refresh failures internally, so rejections here are
  // unexpected (e.g. misconfiguration) — don't cache them.
  promise.catch(() => sessionsByRefreshToken.delete(key));
  return promise;
}
