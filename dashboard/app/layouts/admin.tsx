import { Outlet, redirect, Link, useLocation, useLoaderData } from "react-router";
import { AppShell, Avatar, Box, Group, Text } from "@mantine/core";
import {
  IconArrowLeft,
  IconBuildingEstate,
  IconCalendarEvent,
  IconLayoutDashboard,
  IconLogout,
  IconStack2,
  IconUserCheck,
  IconUsers,
} from "@tabler/icons-react";
import { userContext } from "~/context";
import { authMiddleware } from "~/middleware/auth";
import type { Route } from "./+types/admin";
import { MainButton } from "~/components/main-button/main-button";
import classes from "~/components/menu/index.module.css";

export const middleware = [authMiddleware];

export async function loader({ context }: Route.LoaderArgs) {
  const user = context.get(userContext);

  if (!user || user.role !== "admin") {
    throw redirect("/");
  }

  return { user };
}

const navItems = [
  { label: "Dashboard", icon: IconLayoutDashboard, path: "/admin" },
  { label: "Users", icon: IconUsers, path: "/admin/users" },
  { label: "Properties", icon: IconBuildingEstate, path: "/admin/properties" },
  { label: "Bookings", icon: IconCalendarEvent, path: "/admin/bookings" },
  { label: "Guests", icon: IconUserCheck, path: "/admin/guests" },
];

export default function AdminLayout() {
  const { user } = useLoaderData<typeof loader>();
  const location = useLocation();

  const isActive = (path: string) => location.pathname === path;

  const userDisplayName =
    user?.given_name && user?.family_name
      ? `${user.given_name} ${user.family_name}`
      : user?.username || user?.email || "User";

  const userInitials =
    user?.given_name && user?.family_name
      ? `${user.given_name[0]}${user.family_name[0]}`
      : userDisplayName.substring(0, 2).toUpperCase();

  return (
    <AppShell
      navbar={{ width: 288, breakpoint: "sm", collapsed: { mobile: true } }}
      withBorder={false}
    >
      <AppShell.Navbar
        style={{
          backgroundColor: "#FFF",
          borderRight: "1px solid #E2E8F0",
          display: "flex",
          flexDirection: "column",
          height: "100vh",
        }}
      >
        <nav className={classes.navbar}>
          <div className={classes.navbarLogo}>
            <Group gap="sm">
              <Box
                style={{
                  width: 32,
                  height: 32,
                  backgroundColor: "#0F172A",
                  borderRadius: 8,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <IconStack2 size={20} color="white" />
              </Box>
              <Text fw={700} size="xl" style={{ letterSpacing: "-0.02em" }}>
                Kasify Admin
              </Text>
            </Group>
          </div>

          <div className={classes.navbarMain}>
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                className={classes.link}
                data-active={isActive(item.path) || undefined}
              >
                <item.icon className={classes.linkIcon} stroke={1.5} />
                <span>{item.label}</span>
              </Link>
            ))}
          </div>

          <div className={classes.navbarBottom}>
            <Link to="/account" className={classes.userProfile}>
              <Avatar size="md" radius="xl" color="blue">
                {userInitials}
              </Avatar>
              <div className={classes.userInfo}>
                <div className={classes.userName}>{userDisplayName}</div>
                {user?.email && (
                  <div className={classes.userEmail}>{user.email}</div>
                )}
              </div>
            </Link>
            <Link
              to="/timeline"
              className={classes.link}
              style={{ marginBottom: 8 }}
            >
              <IconArrowLeft className={classes.linkIcon} stroke={1.5} />
              <span>Back to Dashboard</span>
            </Link>
            <MainButton
              component="a"
              href="/kinde-auth/logout"
              fullWidth={true}
              leftSection={<IconLogout size={18} />}
            >
              Log out
            </MainButton>
          </div>
        </nav>
      </AppShell.Navbar>

      <AppShell.Main
        style={{
          backgroundColor: "#F8FAFC",
          minHeight: "100vh",
        }}
      >
        <Box pt="24px" pb="xl" pl="xl" pr="xl">
          <Outlet />
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}
