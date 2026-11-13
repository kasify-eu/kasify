import { handleAuth } from "@kinde-oss/kinde-remix-sdk";
import { withAuthHint } from "~/middleware/auth";
import type { Route } from "./+types/auth";

export async function loader({ params, request }: Route.ActionArgs) {
  const response = await handleAuth(request, params.action);
  // Drop the marketing-site hint cookie on logout so the visitor isn't bounced
  // straight back into the app from the landing page.
  if (params.action === "logout" && response instanceof Response) {
    return withAuthHint(response, request, false);
  }
  return response;
}
