import { useTranslation } from "react-i18next";
import { IconCheck, IconX } from "@tabler/icons-react";
import type { Guest } from "~/types/bookings";
import classes from "./guest-details-drawer.module.css";
import { calculateAge, countryFlag, formatGuestName, formatSex, initials, tintFor } from "./guest-utils";

interface GuestDetailsHeaderProps {
  guest: Guest;
  isRegistered: boolean;
  sesSubmittedAt?: string | null;
  bookingCode?: string | null;
  onClose: () => void;
  /** Optional action buttons rendered before the close button (e.g. Edit). */
  rightActions?: React.ReactNode;
}

export function GuestDetailsHeader({
  guest,
  isRegistered,
  sesSubmittedAt,
  bookingCode,
  onClose,
  rightActions,
}: GuestDetailsHeaderProps) {
  const { t, i18n } = useTranslation();
  const isEs = i18n.language.startsWith("es");

  const fullName = formatGuestName(guest) || (t("bookingDetails.unknownGuest") as string);
  const age = calculateAge(guest.date_of_birth);
  const sex = formatSex(guest.sex);
  const sexLabel = sex === "Male"
    ? (t("checkinGuest.sexMale") as string)
    : sex === "Female"
      ? (t("checkinGuest.sexFemale") as string)
      : sex;
  const nationality = guest.nationality
    ? (isEs ? guest.nationality.name_es : guest.nationality.name)
    : null;
  const nationalityFlag = countryFlag(guest.nationality?.alpha_2);

  const ageSexParts = [sexLabel, age != null ? String(age) : null].filter(Boolean) as string[];

  return (
    <div className={classes.head}>
      <div className={classes.identity}>
        <div className={classes.av} data-tint={tintFor(guest.id)}>
          {initials(guest.first_name, guest.last_name)}
        </div>
        <div className={classes.identityText}>
          <h2 title={fullName}>{fullName}</h2>
          {(ageSexParts.length > 0 || nationality) && (
            <div className={classes.sub}>
              {ageSexParts.length > 0 && (
                <span>{ageSexParts.join(" · ")}</span>
              )}
              {nationality && ageSexParts.length > 0 && <span className={classes.sep} />}
              {nationality && (
                <span className={classes.countryLine}>
                  {nationalityFlag ? (
                    <span className={classes.flag}>{nationalityFlag}</span>
                  ) : (
                    <span className={classes.swatch} />
                  )}
                  {nationality}
                </span>
              )}
            </div>
          )}
          <div className={classes.pillRow}>
            {isRegistered ? (
              <span className={`${classes.pill} ${classes.pillGreen}`}>
                <IconCheck stroke={2.4} />
                {t("guestDetailsDrawer.registered") as string}
              </span>
            ) : (
              <span className={`${classes.pill} ${classes.pillAmber}`}>
                {t("guestDetailsDrawer.notRegistered") as string}
              </span>
            )}
            {sesSubmittedAt && (
              <span className={`${classes.pill} ${classes.pillBlue}`}>
                {t("guestDetailsDrawer.sesSynced") as string}
              </span>
            )}
            {bookingCode && (
              <span className={classes.pill}>
                {t("guestDetailsDrawer.bookingCode", { code: bookingCode }) as string}
              </span>
            )}
          </div>
        </div>
        <div className={classes.headActions}>
          {rightActions}
          <button
            type="button"
            className={`${classes.iconBtn} ${classes.iconBtnClose}`}
            onClick={onClose}
            aria-label={t("bookingDetails.close") as string}
          >
            <IconX stroke={2} />
          </button>
        </div>
      </div>
    </div>
  );
}
