import { Outlet } from "react-router";
import { AppShell, Box } from "@mantine/core";
import { MainMenu } from "~/components/menu";
import { MobileNav } from "~/components/mobile-nav";
import { NavigationProgress } from "~/components/navigation-progress";
import { useLoaderData } from "react-router";
import { userContext } from "~/context";
import { authMiddleware } from "~/middleware/auth";
import type { Route } from "./+types/dashboard";

export const middleware = [authMiddleware];

export async function loader({ context, request }: Route.LoaderArgs) {
  const user = context.get(userContext);
  return { user: user };
}

export default function DashboardLayout() {
  const { user } = useLoaderData<typeof loader>();

  return (
    <AppShell
      navbar={{ width: 288, breakpoint: "sm", collapsed: { mobile: true } }}
      withBorder={false}
    >
      <NavigationProgress />
      <AppShell.Navbar
        style={{
          backgroundColor: "#FFF",
          borderRight: "1px solid #E2E8F0",
          display: "flex",
          flexDirection: "column",
          height: "100vh",
        }}
      >
        <MainMenu user={user} />
      </AppShell.Navbar>

      <AppShell.Main
        style={{
          backgroundColor: "#F8FAFC",
          minHeight: "100vh",
        }}
      >
        <Box
          pt="24px"
          pb={{ base: 80, sm: "xl" }}
          pl={{ base: 0, sm: "xl" }}
          pr={{ base: 0, sm: "xl" }}
        >
          <Outlet />
        </Box>
      </AppShell.Main>
      <MobileNav />
    </AppShell>
  );
}
