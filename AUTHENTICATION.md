# Authentication setup (Kinde)

Kasify currently delegates login to **Kinde**, so you need a Kinde account to run it: both the API (JWT validation, machine-to-machine token) and the dashboard (OAuth flow) are configured through it. Supporting any OpenID Connect provider is a known gap and a welcome contribution.

Useful links:

- Kinde website: [kinde.com](https://kinde.com) (the free tier is enough). Manage your account and applications at [app.kinde.com](https://app.kinde.com).
- Kinde documentation: [docs.kinde.com](https://docs.kinde.com). Search it for *user properties*, *token customization*, *roles* and *machine to machine applications*, the four concepts used below. Kinde moves its menus around from time to time; if a path below no longer matches, the docs are the source of truth.
- Hosted Kasify (for comparison): [kasify.eu](https://kasify.eu), user guide at [docs.kasify.eu](https://docs.kasify.eu).

## How Kasify uses Kinde

1. The **dashboard** logs users in with the Kinde OAuth flow (`/kinde-auth/login`, `/kinde-auth/callback`, `/kinde-auth/logout`) and forwards the access token to the API.
2. The **API** validates every token against the public keys published at `KINDE_DOMAIN` (JWKS). It then reads two custom claims from the token to know who is calling: `k_uid` (Kasify user id) and `k_aid` (Kasify account id).
3. Those claims do not exist when a user first signs up. After the user completes `/signup`, the API creates the Kasify user and account and **writes `k_uid` and `k_aid` back to the Kinde user** through the Kinde Management API, using a machine-to-machine token. The dashboard then forces a fresh login (`/refresh`) so the new token carries both properties.

So you must set up in Kinde: a web application, a machine-to-machine application, two user properties that are included in the access token, and (optionally) an `admin` role.

## 1. Create the web application (dashboard login)

1. In [app.kinde.com](https://app.kinde.com) go to **Settings → Applications → Add application**, choose **Back-end web** (it has a client secret) and name it e.g. `Kasify`.
2. Open the application's **Details** page and set:
   - **Allowed callback URLs**: `http://localhost:3000/kinde-auth/callback` (Docker Compose) and/or `http://localhost:5173/kinde-auth/callback` (dev server). In production use your own dashboard URL, e.g. `https://app.example.com/kinde-auth/callback`.
   - **Allowed logout redirect URLs**: the dashboard URL (`http://localhost:3000`, `http://localhost:5173`, or your production URL).
3. Copy the **Domain**, **Client ID** and **Client secret**.

| Kinde value | Variable | File |
|---|---|---|
| Domain, e.g. `https://yourname.kinde.com` | `KINDE_DOMAIN` (API and Compose), `KINDE_ISSUER_URL` (dashboard) | `.env`, `api/.env`, `dashboard/.env` |
| Client ID | `KINDE_CLIENT_ID` | `.env`, `dashboard/.env` |
| Client secret | `KINDE_CLIENT_SECRET` | `.env`, `dashboard/.env` |

In the dashboard `.env`, also set `KINDE_SITE_URL`, `KINDE_POST_LOGIN_REDIRECT_URL` and `KINDE_POST_LOGOUT_REDIRECT_URL` to the URL where the dashboard runs. They must match the URLs allowed in Kinde. With Docker Compose these are derived from `DASHBOARD_BASE_URL`.

## 2. Create the user properties (`k_uid` and `k_aid`)

Go to **Settings → Properties → Add property** and create two properties:

| Name | Key (exact) | Type | Applies to |
|---|---|---|---|
| Kasify user id | `k_uid` | Text | Users |
| Kasify account id | `k_aid` | Text | Users |

The keys must be exactly `k_uid` and `k_aid`; the API looks them up by key. Leave them empty: Kasify fills them in after signup.

## 3. Include the properties (and roles) in the access token

Creating the properties is not enough, they also have to be added to the JWT. In your web application, open the **Tokens** tab and customize the **access token** so it includes:

- **User properties** `k_uid` and `k_aid`. The token then carries a `user_properties` claim shaped like this, which is exactly what the API and dashboard parse:

  ```json
  {
    "user_properties": {
      "k_uid": { "v": "6f1c…-user-uuid" },
      "k_aid": { "v": "a02d…-account-uuid" }
    }
  }
  ```

- **Roles** (optional, see below). They appear in the `roles` claim as a list of `{ "id", "key", "name" }` objects.

Until a user has completed signup, `user_properties` is empty and the dashboard sends them to `/signup`. That is expected. If a user keeps landing on `/signup` after finishing it, the properties are not in the token (check this step) or the M2M app below lacks permission to write them.

## 4. Create the machine-to-machine application (API → Kinde)

1. **Settings → Applications → Add application**, choose **Machine to machine**, name it e.g. `Kasify API`.
2. Open it, go to **APIs**, and authorize the **Kinde Management API**.
3. Enable the scope that lets it write user properties: `update:user_properties`. (Add `read:user_properties` if you want to inspect them through the same app.)
4. Copy its **Client ID** and **Client secret** into `KINDE_M2M_CLIENT_ID` and `KINDE_M2M_CLIENT_SECRET` (`api/.env`, or `.env` for Docker Compose).

The API requests a token from `<KINDE_DOMAIN>/oauth2/token` with the `client_credentials` grant and audience `<KINDE_DOMAIN>/api`, caches it, and uses it for `PUT <KINDE_DOMAIN>/api/v1/users/{id}/properties/{k_uid|k_aid}`. If signup creates the Kasify account but the user keeps being sent back to `/signup`, check the API logs for `Failed to update Kinde user properties`: it is almost always a missing scope or a wrong M2M client id/secret.

## 5. Create the `admin` role (optional)

Only needed if you want access to the admin routes (SES monitoring, geo lists and similar). In **Settings → Roles → Add role**, create a role with key `admin` (exact), make sure roles are included in the access token (step 3), and assign it to your user under **Users → your user → Roles**. Users without it are regular users.

## Verify it works

1. Start the API and dashboard, open the dashboard and sign up with a new user.
2. After the signup form you should land in the app, not back on `/signup`.
3. In Kinde, open the user and check that **Properties** shows `k_uid` and `k_aid` with UUID values.
4. To inspect the token, decode the `access_token` cookie locally (e.g. `cut -d. -f2 | base64 -d`) and look for the `user_properties` (and `roles`) claims. Do not paste production tokens into third-party sites.

For the token refresh behaviour (`/session-expired` vs `/refresh`) see [`dashboard/AUTH_FLOW.md`](dashboard/AUTH_FLOW.md).
