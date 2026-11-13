import {
  IconApps,
  IconCalendar,
  IconCalendarMonth,
  IconTicket,
  IconChecklist,
  IconMenu2,
  IconBuilding,
  IconUser,
  IconUsers,
  IconLogout,
} from "@tabler/icons-react";
import { Link, useLocation } from "react-router";
import { useState } from "react";
import { Drawer, Divider, Stack } from "@mantine/core";
import { useTranslation } from "react-i18next";
import { LanguageSelector } from "~/components/language-selector";
import classes from "./index.module.css";

export function MobileNav() {
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { t } = useTranslation();

  const navItems = [
    { labelKey: "nav.timeline", icon: IconCalendar, path: "/timeline" },
    { labelKey: "nav.calendar", icon: IconCalendarMonth, path: "/calendar" },
    { labelKey: "nav.bookings", icon: IconTicket, path: "/bookings" },
  ];

  const moreItems = [
    { labelKey: "nav.tasks", icon: IconChecklist, path: "/tasks" },
    { labelKey: "nav.guests", icon: IconUsers, path: "/guests" },
    { labelKey: "nav.properties", icon: IconBuilding, path: "/properties" },
    { labelKey: "nav.integrations", icon: IconApps, path: "/integrations" },
    { labelKey: "nav.account", icon: IconUser, path: "/account" },
  ];

  const isActive = (path: string) => {
    return (
      location.pathname === path || location.pathname.startsWith(path + "/")
    );
  };

  return (
    <>
      <nav className={classes.mobileNav}>
        {navItems.map((item) => (
          <Link
            key={item.labelKey}
            to={item.path}
            className={classes.navItem}
            data-active={isActive(item.path) || undefined}
          >
            <item.icon size={22} stroke={1.5} />
            <span className={classes.navLabel}>{t(item.labelKey)}</span>
          </Link>
        ))}
        <button
          className={classes.navItem}
          onClick={() => setDrawerOpen((o) => !o)}
          data-active={
            moreItems.some((item) => isActive(item.path)) || undefined
          }
        >
          <IconMenu2 size={22} stroke={1.5} />
          <span className={classes.navLabel}>{t("nav.more")}</span>
        </button>
      </nav>

      <Drawer
        opened={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        position="bottom"
        zIndex={100}
        withCloseButton={false}
        styles={{
          content: { borderRadius: "16px 16px 0 0", height: "fit-content", paddingBottom: "60px" },
          inner: { bottom: 0 },
        }}
      >
        <Stack gap="xs" pb="md">
          {moreItems.map((item) => (
            <Link
              key={item.labelKey}
              to={item.path}
              className={classes.drawerItem}
              onClick={() => setDrawerOpen(false)}
            >
              <item.icon size={20} stroke={1.5} />
              <span>{t(item.labelKey)}</span>
            </Link>
          ))}
          <a href="/kinde-auth/logout" className={classes.drawerItem}>
            <IconLogout size={20} stroke={1.5} />
            <span>{t("nav.signOut")}</span>
          </a>
          <Divider mt="xs" />
          <LanguageSelector />
        </Stack>
      </Drawer>
    </>
  );
}
