import {index, layout, route, type RouteConfig,} from "@react-router/dev/routes";

export default [
  // System routes
  route(".well-known/*", "routes/well-known.tsx"),
  route("api/set-locale", "routes/api.set-locale.tsx"),
  route("api/*", "routes/api.$.tsx"),
  route("health", "routes/health.tsx"),

  // Auth routes
  route("kinde-auth/:action", "routes/kinde-auth/auth.tsx"),
  route("signup", "routes/signup.tsx"),
  route("refresh", "routes/refresh.tsx"),
  route("session-expired", "routes/session-expired.tsx"),

  // Dashboard routes (authenticated)
  layout("layouts/dashboard.tsx", [
    route("timeline", "routes/dashboard/timeline/timeline.tsx"),
    route("welcome", "routes/dashboard/welcome/welcome.tsx"),
    route("calendar", "routes/dashboard/calendar/calendar.tsx"),

    // Properties
    route("properties", "routes/dashboard/properties/properties.tsx"),
    route("properties/new", "routes/dashboard/properties/properties-new.tsx"),
    route("properties/:id", "routes/dashboard/properties/property-details.tsx"),
    route("properties/:id/settings", "routes/dashboard/properties/property-settings.tsx"),

    // Bookings
    route("bookings", "routes/dashboard/bookings/index.tsx"),
      route("bookings/new", "routes/dashboard/bookings/new.tsx"),

    // Guests
    route("guests", "routes/dashboard/guests/index.tsx"),

      // Tasks
      route("tasks", "routes/dashboard/tasks/index.tsx"),
      route("tasks/new", "routes/dashboard/tasks/new.tsx"),

    // Integrations
    route("integrations", "routes/dashboard/integrations/index.tsx"),
    route("integrations/nuki", "routes/dashboard/integrations/nuki.tsx"),
    route("integrations/nuki/:id", "routes/dashboard/integrations/nuki-details.tsx"),
    route("integrations/unifi", "routes/dashboard/integrations/unifi.tsx"),
    route("integrations/unifi/:id", "routes/dashboard/integrations/unifi-details.tsx"),
    route("integrations/ses-hospedajes", "routes/dashboard/integrations/ses-hospedajes.tsx"),
    route("integrations/ses-hospedajes/:id", "routes/dashboard/integrations/ses-hospedajes-details.tsx"),

    // Account
    route("account", "routes/dashboard/account/index.tsx"),
  ]),

  // Admin routes (admin role required)
  layout("layouts/admin.tsx", [
    route("admin", "routes/admin/dashboard.tsx"),
    route("admin/users", "routes/admin/users.tsx"),
    route("admin/properties", "routes/admin/properties.tsx"),
    route("admin/bookings", "routes/admin/bookings.tsx"),
    route("admin/guests", "routes/admin/guests.tsx"),
  ]),

    // Public routes (no auth required)
    route("tasks/public", "routes/tasks/public.tsx"),
    route("unsubscribe/:token", "routes/unsubscribe.tsx"),
    layout("layouts/checkin.tsx", [
      route("checkin/:bookingCode", "routes/checkin/welcome.tsx"),
      route("checkin/:bookingCode/guest", "routes/checkin/guest.tsx"),
    ]),

  // Landing page
  index("routes/home.tsx"),
] satisfies RouteConfig;
