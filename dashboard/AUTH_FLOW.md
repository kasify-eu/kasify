# Authentication Flow & Token Refresh

This document describes how the application handles JWT token expiration and automatic re-authentication.

## Overview

The application uses Kinde OAuth for authentication with JWT tokens. When a user's session expires (e.g., laptop closed overnight), all API calls will return 401 Unauthorized errors. This system automatically handles token refresh without requiring manual login.

## Important: Two Separate Routes

The application has **two different authentication refresh routes** for different purposes:

1. **`/refresh`** - Used by signup workflow to fetch updated claims (ALWAYS forces re-auth)
2. **`/session-expired`** - Used for handling expired tokens (conditional re-auth)

These routes must remain separate to avoid breaking the signup flow.

## How It Works

### 1. **401 Detection (API Client)**

The `ApiClient` (`app/lib/api-client/index.tsx`) intercepts all HTTP responses:

```typescript
// Handle 401 Unauthorized - token expired or invalid
if (response.status === 401 && isBrowserClient) {
  this.handle401Unauthorized();
  throw new ApiClientError("Authentication expired...", 401);
}
```

### 2. **Automatic Redirect**

When a 401 is detected:

1. Shows a toast notification: "Session expired. Refreshing authentication..."
2. Captures the current URL (including query params)
3. Redirects to `/session-expired?redirectTo=/current/path`

```typescript
private handle401Unauthorized(): void {
  const currentPath = window.location.pathname + window.location.search;
  // Note: Uses /session-expired (NOT /refresh) to preserve signup workflow
  const expiredUrl = `/session-expired?redirectTo=${encodeURIComponent(currentPath)}`;
  window.location.href = expiredUrl;
}
```

### 3. **Re-authentication (Session Expired Route)**

The `/session-expired` route (`app/routes/session-expired.tsx`) handles expired sessions:

1. Checks if user already has a valid token
2. If yes, immediately redirects to the stored `redirectTo` URL (no re-auth needed)
3. If no, triggers Kinde's `handleAuth` to silently refresh the token

```typescript
export async function loader({ request }) {
  const url = new URL(request.url);
  const redirectTo = url.searchParams.get("redirectTo") || "/timeline";

  const { getToken } = await getKindeSession(request);
  const token = await getToken();

  if (token) {
    // Already authenticated, redirect back immediately
    return redirect(redirectTo);
  }

  // No valid token, trigger silent re-authentication
  // Redirect to login with returnTo parameter to specify where to go after auth
  const loginUrl = `/kinde-auth/login?returnTo=${encodeURIComponent(redirectTo)}`;
  return redirect(loginUrl);
}
```

### Why Two Routes?

**`/refresh` (Signup Workflow)**
- ALWAYS forces re-authentication
- Fetches updated claims (k_uid, k_aid) after account creation
- Ignores existing token validity
- Used only during signup

**`/session-expired` (Token Expiration)**
- Conditionally re-authenticates
- Checks if token is still valid first
- Only re-authenticates if needed
- Used for all 401 errors from API

### 4. **Silent Re-authentication**

Kinde's OAuth flow:
- Detects user is already authenticated (via session cookie)
- Issues new JWT tokens with fresh expiration
- **Does NOT show login screen** (silent refresh)
- Returns user to original page

## User Experience

### Scenario: User Opens Laptop After 24 Hours

1. **User action**: Opens booking details panel
2. **API call**: `GET /bookings/123` → Returns 401
3. **System**: Shows toast "Session expired. Refreshing..."
4. **Redirect**: Browser goes to `/session-expired?redirectTo=/bookings`
5. **Kinde**: Silently refreshes tokens (no login screen)
6. **Return**: User back at `/bookings` with fresh session
7. **Total time**: ~1-2 seconds

### Visual Flow (Token Expiration)

```
User Action → 401 Error → Toast → /session-expired → Check Token
                                                           ↓
                                                   Valid? → Redirect Back
                                                           ↓
                                                   Invalid? → Kinde OAuth → New Tokens → Redirect Back
```

### Visual Flow (Signup)

```
Signup Complete → /refresh → ALWAYS Re-auth → Fetch Claims → Redirect to App
```

## Benefits

✅ **Automatic**: No manual re-login required
✅ **Seamless**: User stays on the same page
✅ **Silent**: No login screen if session cookie valid
✅ **Informative**: Toast shows what's happening
✅ **Universal**: Works for all API calls automatically

## Browser vs Server Behavior

- **Browser clients**: Automatic 401 handling with redirect
- **Server clients**: Throw error (handled by route loaders)

Only browser-based API calls (`ApiClient.createFromBrowser()`) trigger the automatic redirect.

## Configuration

No configuration needed. The system works automatically for:

- All `ApiClient` methods (GET, POST, PATCH, DELETE, etc.)
- All authenticated routes
- All components making API calls

## Testing

### Test Token Expiration (Session Expired Route)

```typescript
// Navigate to any protected page
window.location.href = "/bookings/123";

// Manually expire token by clearing cookies or waiting for expiration
// Then make any API call:
const apiClient = ApiClient.createFromBrowser(createApiConfig());
await apiClient.getBookings(); // Will trigger 401 → /session-expired redirect

// Expected flow:
// 1. Toast shows "Session expired..."
// 2. Redirects to /session-expired?redirectTo=/bookings/123
// 3. Checks for valid token
// 4. If valid: immediate redirect to /bookings/123
// 5. If invalid: Kinde re-auth → redirect to /bookings/123
```

### Test Signup Flow (Refresh Route)

```typescript
// Complete signup process
// App redirects to /refresh

// Expected flow:
// 1. ALWAYS triggers Kinde re-authentication
// 2. Fetches updated claims (k_uid, k_aid)
// 3. Redirects to app (e.g., /timeline)
// 4. Does NOT check for existing valid token
```

### Test Redirect Back

```typescript
// Navigate to specific page
window.location.href = "/properties/123?tab=details";

// Clear authentication or trigger 401
// System should:
// 1. Redirect to /session-expired?redirectTo=/properties/123?tab=details
// 2. After re-auth, return to /properties/123?tab=details
// 3. Query params should be preserved
```

## Troubleshooting

### Issue: Infinite redirect loop

**Cause**: `/session-expired` route itself returns 401
**Solution**: Ensure Kinde session cookie is valid

### Issue: Signup workflow broken after re-authentication

**Cause**: Using `/session-expired` instead of `/refresh` for signup
**Solution**: Signup MUST use `/refresh` to force claim fetch. Only 401 errors should use `/session-expired`

### Issue: Updated claims not reflected after signup

**Cause**: `/session-expired` was used instead of `/refresh`
**Solution**: Ensure signup flow uses `/refresh` which ALWAYS forces re-authentication

### Issue: Lost form data

**Cause**: Full page redirect clears form state
**Solution**: Use form persistence or optimistic updates before API calls

### Issue: Toast doesn't show

**Cause**: Toast library not loaded
**Solution**: System gracefully handles this; redirect still works

## Related Files

- `app/lib/api-client/index.tsx` - API client with 401 handling
- `app/routes/session-expired.tsx` - Handles expired tokens (401 errors)
- `app/routes/refresh.tsx` - Handles signup claim refresh (ALWAYS re-auth)
- `app/lib/toast.ts` - Toast notifications
- `AUTH_FLOW.md` - This documentation

## Future Enhancements

Potential improvements:

1. **Token refresh before expiration** - Proactively refresh tokens
2. **Retry failed request** - After re-auth, retry original request
3. **Loading indicator** - Show spinner during refresh
4. **Rate limiting** - Prevent rapid refresh attempts
