import {
  IconApps,
  IconCalendar,
  IconCalendarMonth,
  IconChecklist,
  IconHome,
  IconLogout,
  IconSettings,
  IconStack2,
  IconTicket,
  IconUsers,
} from "@tabler/icons-react";
import { Link, useLocation } from "react-router";
import {Avatar, Box, Divider, Group, Image, Text} from "@mantine/core";
import { useTranslation } from "react-i18next";
import type { SessionUser } from "~/types/user";
import classes from "./index.module.css";
import { MainButton } from "~/components/main-button/main-button";
import { LanguageSelector } from "~/components/language-selector";
import { getDocsUrl } from "~/lib/public-url";

interface MainMenuProps {
  user: SessionUser | null;
}

export function MainMenu({ user }: MainMenuProps) {
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const docsBase = getDocsUrl();
  const docsUrl = i18n.language?.startsWith("es") ? `${docsBase}/es` : docsBase;

  const navItems = [
    { label: t("nav.timeline"), icon: IconCalendar, path: "/timeline" },
    { label: t("nav.calendar"), icon: IconCalendarMonth, path: "/calendar" },
    { label: t("nav.properties"), icon: IconHome, path: "/properties" },
    { label: t("nav.bookings"), icon: IconTicket, path: "/bookings" },
    { label: t("nav.guests"), icon: IconUsers, path: "/guests" },
    { label: t("nav.tasks"), icon: IconChecklist, path: "/tasks" },
    { label: t("nav.integrations"), icon: IconApps, path: "/integrations" },
  ];

  const isActive = (path: string) => {
    return (
      location.pathname === path || location.pathname.startsWith(path + "/")
    );
  };

  const links = navItems.map((item) => (
    <Link
      to={item.path}
      className={classes.link}
      data-active={isActive(item.path) || undefined}
      key={item.path}
    >
      <item.icon className={classes.linkIcon} stroke={1.5} />
      <span>{item.label}</span>
    </Link>
  ));

  const userDisplayName =
    user?.given_name && user?.family_name
      ? `${user.given_name} ${user.family_name}`
      : user?.username || user?.email || "User";

  const userInitials =
    user?.given_name && user?.family_name
      ? `${user.given_name[0]}${user.family_name[0]}`
      : userDisplayName.substring(0, 2).toUpperCase();

  return (
    <nav className={classes.navbar}>
      <div className={classes.navbarLogo}>
        <Group gap="sm">
          <Image src="/logo.svg" h={28} />
        </Group>
      </div>
      <div className={classes.navbarMain}>{links}</div>
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
        <MainButton
          component="a"
          href="/kinde-auth/logout"
          fullWidth={true}
          leftSection={<IconLogout size={18} />}
        >
          {t("nav.logout")}
        </MainButton>
        <LanguageSelector />
        <Text size="xs" c="dimmed" ta="center" mt={2}>
          <a
            href={docsUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={classes.docsLink}
          >
            {t("nav.documentation")}
          </a>
          {" · "}
          {__APP_VERSION__}
        </Text>
      </div>
    </nav>
  );
}
