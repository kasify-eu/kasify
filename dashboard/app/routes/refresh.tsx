import { handleAuth } from "@kinde-oss/kinde-remix-sdk";

/**
 * This route forces a full re-authentication to fetch updated user claims.
 * After account creation, the user's claims (k_uid, k_aid) are updated in Kinde,
 * but refreshTokens() doesn't fetch them. We need to force a new login flow.
 *
 * IMPORTANT: This is used by the signup workflow and ALWAYS forces re-authentication
 * regardless of current token validity. For expired session handling, see session-expired.tsx
 */
// @ts-ignore
export async function loader({ request }) {
    // ALWAYS redirect to login to force fetching fresh claims
    // Kinde will see the user is already authenticated and will issue new tokens
    // with fresh claims without showing the login screen
    return await handleAuth(request, "login");
}
