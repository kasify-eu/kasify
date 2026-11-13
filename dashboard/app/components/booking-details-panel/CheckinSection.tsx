import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { showSuccessToast, showErrorToast } from "~/lib/toast";
import { getPublicAppUrl } from "~/lib/public-url";
import { computeSesState, pvAltaComm } from "~/lib/ses-status";
import type { Booking } from "~/types/bookings";
import shared from "~/components/details-drawer/details-drawer.module.css";
import local from "./booking-details-panel.module.css";

const classes = { ...shared, ...local };

function calculateAge(dateOfBirth: string): number | null {
  const dob = new Date(dateOfBirth);
  if (isNaN(dob.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const monthDiff = now.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < dob.getDate())) {
    age--;
  }
  return age >= 0 ? age : null;
}

function formatSesDateTime(iso: string, lang: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "";
  const date = d.toLocaleDateString(lang, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
  const time = d.toLocaleTimeString(lang, { hour: "2-digit", minute: "2-digit" });
  return `${date} · ${time}`;
}

function initials(first?: string | null, last?: string | null): string {
  const a = (first || "").trim()[0] || "";
  const b = (last || "").trim()[0] || "";
  return (a + b).toUpperCase() || "—";
}

function countryFlag(alpha2?: string | null): string | null {
  if (!alpha2 || alpha2.length !== 2) return null;
  const code = alpha2.toUpperCase();
  if (!/^[A-Z]{2}$/.test(code)) return null;
  const base = 0x1f1e6;
  return String.fromCodePoint(
    base + code.charCodeAt(0) - 65,
    base + code.charCodeAt(1) - 65,
  );
}

const CopyIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="9" width="12" height="12" rx="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);

const CheckIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

const ShieldCheckIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    <path d="M9 12l2 2 4-4" />
  </svg>
);

const ShieldAlertIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    <path d="M12 8v4M12 16h.01" />
  </svg>
);

const ClockIcon = (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </svg>
);

interface CheckinSectionProps {
  sectionNum: string;
  booking: Booking;
  onGuestClick?: (guestId: string) => void;
  /**
   * Owner/Manager-only callback fired when an "awaiting" placeholder row is
   * clicked. `slotIndex` is 1-based among all expected guests (including
   * already-registered ones) — the drawer uses it as a header hint.
   */
  onPendingSlotClick?: (slotIndex: number) => void;
}

export function CheckinSection({
  sectionNum,
  booking,
  onGuestClick,
  onPendingSlotClick,
}: CheckinSectionProps) {
  const { t, i18n } = useTranslation();
  const [linkCopied, setLinkCopied] = useState(false);

  const isCheckinCompleted = !!booking.checkin_completed_at;
  const sesState = computeSesState(booking);
  const pvAlta = pvAltaComm(booking);
  const totalGuests =
    (booking.adults_count ?? 0) +
    (booking.children_count ?? 0) +
    (booking.infants_count ?? 0);
  const registeredCount = booking.guests?.length ?? 0;
  const cappedRegistered = Math.min(registeredCount, Math.max(totalGuests, registeredCount));
  const displayTotal = Math.max(totalGuests, registeredCount);
  const pct = displayTotal > 0 ? (cappedRegistered / displayTotal) * 100 : 0;

  const checkinLink = booking.checkin_code
    ? `${getPublicAppUrl()}/checkin/${booking.checkin_code}`
    : null;

  const handleCopyLink = async () => {
    if (!checkinLink) return;
    try {
      await navigator.clipboard.writeText(checkinLink);
      setLinkCopied(true);
      showSuccessToast(t("bookingDetails.toast.linkCopied"));
      setTimeout(() => setLinkCopied(false), 2000);
    } catch {
      showErrorToast(t("bookingDetails.failedToCopy"));
    }
  };

  const emptySlotCount = Math.max(0, displayTotal - registeredCount);

  let sesBanner: React.ReactNode = null;
  if (sesState) {
    const variantClass =
      sesState === "sent"
        ? classes.sesOk
        : sesState === "error"
          ? classes.sesFail
          : classes.sesPending;
    const icon =
      sesState === "sent"
        ? ShieldCheckIcon
        : sesState === "error"
          ? ShieldAlertIcon
          : ClockIcon;
    const titleKey =
      sesState === "sent"
        ? "bookingDetails.ses.registered.title"
        : sesState === "error"
          ? "bookingDetails.ses.failed.title"
          : "bookingDetails.ses.processing.title";

    let meta: React.ReactNode;
    if (sesState === "sent") {
      meta = (
        <>
          {t("bookingDetails.ses.submittedOn", {
            date: pvAlta?.submitted_at
              ? formatSesDateTime(pvAlta.submitted_at, i18n.language)
              : "—",
          })}
          {pvAlta?.lote && (
            <>
              {" "}
              · {t("bookingDetails.ses.ref")}{" "}
              <span className={classes.sesRef}>{pvAlta.lote}</span>
            </>
          )}
        </>
      );
    } else if (sesState === "error") {
      const when = pvAlta?.last_attempt_at ?? pvAlta?.submitted_at;
      const reason =
        pvAlta?.last_error ?? (t("bookingDetails.ses.genericError") as string);
      meta = (
        <>
          {when && <>{formatSesDateTime(when, i18n.language)} · </>}
          {reason}
        </>
      );
    } else {
      meta = pvAlta?.submitted_at ? (
        <>
          {t("bookingDetails.ses.submittedOn", {
            date: formatSesDateTime(pvAlta.submitted_at, i18n.language),
          })}{" "}
          · {t("bookingDetails.ses.awaitingConfirmation")}
        </>
      ) : (
        (t("bookingDetails.ses.processingNoDate") as string)
      );
    }

    sesBanner = (
      <div className={`${classes.sesBanner} ${variantClass}`}>
        <div className={classes.sesIcon}>{icon}</div>
        <div className={classes.sesMain}>
          <div className={classes.sesTitle}>{t(titleKey) as string}</div>
          <div className={classes.sesMeta}>{meta}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("bookingDetails.checkinSection") as string}
        </div>
        {isCheckinCompleted ? (
          <span className={`${classes.pill} ${classes.pillGreen}`}>
            {CheckIcon}
            {t("bookingDetails.checkinCompleted") as string}
          </span>
        ) : (
          <span className={`${classes.pill} ${classes.pillAmber}`}>
            <span className="dotp" />
            {t("bookingDetails.checkinPending") as string}
          </span>
        )}
      </div>

      {sesBanner}

      <div className={classes.fieldLabel} style={{ marginBottom: 6 }}>
        {t("bookingDetails.guestCheckinLink") as string}
      </div>
      {totalGuests === 0 ? (
        <div className={classes.ciAlert}>
          {t("bookingDetails.updateGuestCountToShareLink") as string}
        </div>
      ) : checkinLink ? (
        <div className={classes.ciLink}>
          <input type="text" readOnly value={checkinLink} className="url" />
          <button
            type="button"
            className={`copyBtn ${linkCopied ? "copied" : ""}`}
            onClick={handleCopyLink}
            aria-label={t("bookingDetails.copyToClipboard") as string}
          >
            {linkCopied ? CheckIcon : CopyIcon}
          </button>
        </div>
      ) : (
        <div className={classes.ciLink}>
          <div className="url" style={{ color: "var(--ink-4)" }}>—</div>
        </div>
      )}

      {totalGuests > 0 && (
        <>
          <div style={{ height: 18 }} />

          <div className={classes.guestProgress}>
            <span className="frac">
              {cappedRegistered} / {displayTotal}
            </span>
            <div className="rail">
              <div className="fill" style={{ width: `${pct}%` }} />
            </div>
            <span className="label">{t("bookingDetails.registered") as string}</span>
          </div>

          {booking.guests?.map((guest) => {
            const age = guest.date_of_birth ? calculateAge(guest.date_of_birth) : null;
            const clickable = !!onGuestClick;
            const handleClick = () => onGuestClick?.(guest.id);
            const cardProps = clickable
              ? {
                  role: "button" as const,
                  tabIndex: 0,
                  onClick: handleClick,
                  onKeyDown: (e: React.KeyboardEvent<HTMLDivElement>) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      handleClick();
                    }
                  },
                  style: { cursor: "pointer" },
                  "aria-label": `${guest.first_name} ${guest.last_name}`.trim(),
                }
              : {};
            return (
              <div key={guest.id} className={classes.guestCard} {...cardProps}>
                <div className={`${classes.guestAvatar} ${classes.guestAvatarDone}`}>
                  {initials(guest.first_name, guest.last_name)}
                </div>
                <div className={classes.guestName}>
                  {(() => {
                    const flag = countryFlag(guest.nationality?.alpha_2);
                    return flag ? (
                      <span
                        aria-label={guest.nationality?.name ?? undefined}
                        title={guest.nationality?.name ?? undefined}
                        style={{ marginRight: 6 }}
                      >
                        {flag}
                      </span>
                    ) : null;
                  })()}
                  {guest.first_name} {guest.last_name}
                  {age !== null && <span className="age">· {age}</span>}
                </div>
                <div className={`${classes.guestStatus} ${classes.guestStatusDone}`}>
                  {CheckIcon}
                  {t("bookingDetails.registered") as string}
                </div>
              </div>
            );
          })}
          {Array.from({ length: emptySlotCount }).map((_, idx) => {
            const slotNumber = registeredCount + idx + 1;
            const clickable = !!onPendingSlotClick;
            const handleClick = () => onPendingSlotClick?.(slotNumber);
            const cardProps = clickable
              ? {
                  role: "button" as const,
                  tabIndex: 0,
                  onClick: handleClick,
                  onKeyDown: (e: React.KeyboardEvent<HTMLDivElement>) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      handleClick();
                    }
                  },
                  style: { cursor: "pointer" },
                  "aria-label": t("bookingDetails.guestN", { n: slotNumber }) as string,
                }
              : {};
            return (
              <div
                key={`empty-${idx}`}
                className={`${classes.guestCard} ${classes.guestCardEmpty}`}
                {...cardProps}
              >
                <div className={classes.guestAvatar}>—</div>
                <div className={classes.guestName}>
                  {t("bookingDetails.guestN", { n: slotNumber }) as string}
                </div>
                <div className={classes.guestStatus}>
                  {t("bookingDetails.awaiting") as string}
                </div>
              </div>
            );
          })}
        </>
      )}
    </div>
  );
}
