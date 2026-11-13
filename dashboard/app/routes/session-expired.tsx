import { redirect } from "react-router";
import { getDedupedKindeSession } from "~/lib/kinde-session";

/**
 * This route handles expired JWT tokens (401 errors).
 * When a user's session expires (e.g., laptop closed overnight),
 * the API client redirects here to refresh authentication.
 *
 * Flow:
 * 1. Check if user already has a valid token → redirect back to page
 * 2. If not, trigger silent re-authentication via Kinde
 * 3. After re-auth, redirect back to original page
 *
 * IMPORTANT: This is different from /refresh which ALWAYS forces re-authentication
 * for the signup workflow. This route only re-authenticates if needed.
 */
// Only allow same-site, absolute-path redirects to prevent open-redirect
// phishing via ?redirectTo=https://evil.com (or protocol-relative //evil.com).
function safeRedirectTo(value: string | null): string {
    if (value && value.startsWith("/") && !value.startsWith("//")) {
        return value;
    }
    return "/timeline";
}

// @ts-ignore
export async function loader({ request }) {
    const url = new URL(request.url);
    const redirectTo = safeRedirectTo(url.searchParams.get("redirectTo"));
    const loginUrl = `/kinde-auth/login?returnTo=${encodeURIComponent(redirectTo)}`;

    // getKindeSession auto-refreshes an expired access token; the rotated
    // access_token/refresh_token cookies land in session.headers and MUST be
    // attached to every redirect, otherwise the browser keeps the stale
    // (single-use) refresh token and the next refresh fails — bouncing the
    // user to a full login, the exact flow this route exists to prevent.
    let sessionHeaders: Headers | undefined;
    try {
        // Check if user already has a valid token
        const session = await getDedupedKindeSession(request);
        sessionHeaders = session.headers;
        const token = await session.getToken();

        if (token) {
            return redirect(redirectTo, { headers: sessionHeaders });
        }

        // No valid token, trigger silent re-authentication
        // Redirect to login with returnTo parameter to specify where to go after auth
        return redirect(loginUrl, { headers: sessionHeaders });
    } catch (error) {
        // If there's an error getting the session, force re-authentication
        console.error("Error checking session:", error);
        return redirect(
            loginUrl,
            sessionHeaders ? { headers: sessionHeaders } : undefined,
        );
    }
}
