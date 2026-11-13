import type React from "react";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";

const classes = { ...shared, ...local };

// Same banner used for the PV registration state in CheckinSection — extracted so the RH state
// in BookingInfoSection renders with identical styling.
export const SesShieldCheckIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    <path d="M9 12l2 2 4-4" />
  </svg>
);

export const SesShieldAlertIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    <path d="M12 8v4M12 16h.01" />
  </svg>
);

export const SesClockIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);

export type SesBannerVariant = "ok" | "fail" | "pending";

export function SesBanner({
  variant,
  icon,
  title,
  meta,
}: {
  variant: SesBannerVariant;
  icon: React.ReactNode;
  title: string;
  meta: React.ReactNode;
}) {
  const variantClass =
    variant === "ok"
      ? classes.sesOk
      : variant === "fail"
        ? classes.sesFail
        : classes.sesPending;

  return (
    <div className={`${classes.sesBanner} ${variantClass}`}>
      <div className={classes.sesIcon}>{icon}</div>
      <div className={classes.sesMain}>
        <div className={classes.sesTitle}>{title}</div>
        <div className={classes.sesMeta}>{meta}</div>
      </div>
    </div>
  );
}
