import { userContext } from "~/context";
import { getDedupedKindeSession } from "~/lib/kinde-session";
import type { SessionUser, UserRole } from "~/types/user";
import { redirect } from "react-router";

interface ClaimValue {
  v: string;
}

interface UserPropertiesClaim {
  k_uid: ClaimValue;
  k_aid?: ClaimValue;
}

interface MiddlewareContext {
  set: (context: any, value: any) => void;
}

// Hint cookie readable by the marketing site (shared parent domain) so it can
// send signed-in visitors straight to the app. It carries no credentials and is
// never trusted for anything: the session itself stays the source of truth.
// Disabled unless AUTH_HINT_COOKIE_DOMAIN is set (e.g. ".example.com").
const AUTH_HINT_COOKIE = "kasify_auth";
const AUTH_HINT_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

export function withAuthHint(
  response: Response,
  request: Request,
  authenticated: boolean,
): Response {
  const domain = process.env.AUTH_HINT_COOKIE_DOMAIN;
  if (!domain) return response;

  const hasHint = (request.headers.get("Cookie") ?? "")
    .split(";")
    .some((c) => c.trim() === `${AUTH_HINT_COOKIE}=1`);
  // Nothing to do for anonymous visitors that never had the hint.
  if (!authenticated && !hasHint) return response;

  const secure = (process.env.VITE_KASIFY_PUBLIC_URL ?? "").startsWith("https:")
    ? "; Secure"
    : "";
  const attrs = `Path=/; Domain=${domain}; SameSite=Lax${secure}`;
  response.headers.append(
    "Set-Cookie",
    authenticated
      ? `${AUTH_HINT_COOKIE}=1; Max-Age=${AUTH_HINT_MAX_AGE_SECONDS}; ${attrs}`
      : `${AUTH_HINT_COOKIE}=; Max-Age=0; ${attrs}`,
  );
  return response;
}

// Public routes that don't require authentication
const PUBLIC_ROUTES = ["/", "/signup"];

// Routes that require authentication but not Kasify claims
const ONBOARDING_ROUTES = ["/signup"];

export const authMiddleware = async (
  {
    request,
    context,
  }: {
    request: Request;
    context: MiddlewareContext;
  },
  next: () => Promise<Response>,
) => {
  // The session auto-refreshes the access token when expired. The rotated
  // cookies land in session.headers — we must merge them into the downstream
  // response, otherwise the browser keeps the stale refresh_token and the
  // next refresh fails (single-use rotation), bouncing the user to login.
  // Deduped so concurrent requests don't race to redeem the same single-use
  // refresh token.
  const session = await getDedupedKindeSession(request);

  try {
    const token = await session.getToken();
    const authenticated = token !== null && (await session.isAuthenticated());

    const user = authenticated ? await session.getUser() : null;
    const { pathname } = new URL(request.url);

    // Allow unauthenticated access to the root (it shows the sign-in page)
    if (user === null && pathname === "/") {
      return withAuthHint(
        attachSessionCookies(await next(), session.headers),
        request,
        false,
      );
    }

    // Redirect unauthenticated users to the root, which shows the sign-in page.
    // Exception: if the URL carries an invitation code, preserve it through the
    // Kinde login flow so the invitee lands back on /properties?invitation=CODE
    // after authenticating.
    if (user === null && !PUBLIC_ROUTES.includes(pathname)) {
      const invitation = new URL(request.url).searchParams.get("invitation");
      if (invitation) {
        const returnTo = `/properties?invitation=${encodeURIComponent(invitation)}`;
        console.log(
          `[authMiddleware] Invitation on ${pathname} — redirecting to login with returnTo=${returnTo}`,
        );
        throw withAuthHint(
          redirect(`/kinde-auth/login?returnTo=${encodeURIComponent(returnTo)}`),
          request,
          false,
        );
      }
      console.log(
        `[authMiddleware] No token/user for ${pathname} — redirecting to /`,
      );
      throw withAuthHint(redirect("/"), request, false);
    }

    // For authenticated users, check Kasify claims
    if (user !== null) {
      const userProperties = (await session.getClaimValue(
        "user_properties",
        "access_token",
      )) as UserPropertiesClaim | undefined;

      const kasifyUserId = userProperties?.k_uid?.v;
      const kasifyAccountId = userProperties?.k_aid?.v;

      const userRoles = (await session.getClaimValue(
        "roles",
        "access_token",
      )) as { id: string; key: string; name: string }[] | undefined;

      const role: UserRole = userRoles?.some((r) => r.key === "admin")
        ? "admin"
        : "user";

      // Redirect to signup if missing Kasify claims (except if already on signup)
      if (
        (kasifyUserId === undefined || kasifyAccountId === undefined) &&
        !ONBOARDING_ROUTES.includes(pathname)
      ) {
        console.log(
          "User authenticated but missing Kasify claims. Redirecting to signup",
        );
        throw redirect("/signup");
      }

      // Redirect away from signup if user already has claims
      if (
        kasifyUserId !== undefined &&
        kasifyAccountId !== undefined &&
        ONBOARDING_ROUTES.includes(pathname)
      ) {
        console.log("User already registered, redirecting to timeline");
        throw redirect("/timeline");
      }

      // Validate email exists before using it
      if (!user.email) {
        console.error("User authenticated but email is missing");
        throw redirect("/");
      }

      // Set user context
      const contextUser: SessionUser = {
        id: user.id,
        username: user.email,
        email: user.email,
        given_name: user.given_name,
        family_name: user.family_name,
        role,
      };
      context.set(userContext, contextUser);
    }

    return withAuthHint(
      attachSessionCookies(await next(), session.headers),
      request,
      user !== null,
    );
  } catch (error) {
    // Propagate redirects (and any other Response) — but still attach the
    // refreshed session cookies so a redirect doesn't lose the new tokens.
    if (error instanceof Response) {
      throw attachSessionCookies(error, session.headers);
    }

    console.error("Auth middleware error:", error);
    throw redirect("/");
  }
};

function attachSessionCookies(response: Response, sessionHeaders: Headers): Response {
  for (const [key, value] of sessionHeaders.entries()) {
    response.headers.append(key, value);
  }
  return response;
}
