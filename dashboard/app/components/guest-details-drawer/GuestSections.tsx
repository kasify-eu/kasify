import { useTranslation } from "react-i18next";
import type { Guest } from "~/types/bookings";
import classes from "./guest-details-drawer.module.css";
import { formatDateOfBirth, formatSex } from "./guest-utils";

const DASH = "—";

function Field({
  label,
  value,
  span = 1,
  mono = false,
  hint,
}: {
  label: string;
  value?: string | null;
  span?: 1 | 2;
  mono?: boolean;
  hint?: string;
}) {
  const empty = !value || !value.toString().trim();
  return (
    <div className={`${classes.field} ${span === 2 ? classes.span2 : ""}`}>
      <label className={classes.fieldLabel}>
        {label}
        {hint && <span className={classes.hint}> · {hint}</span>}
      </label>
      <div
        className={`${classes.fieldValue} ${mono ? classes.fieldMono : ""}`}
        data-empty={empty}
      >
        {empty ? DASH : value}
      </div>
    </div>
  );
}

interface SectionProps {
  guest: Guest;
  sectionNum: string;
}

export function PersonalSection({ guest, sectionNum }: SectionProps) {
  const { t, i18n } = useTranslation();
  const isEs = i18n.language.startsWith("es");
  const sex = formatSex(guest.sex);
  const sexLabel = sex === "Male"
    ? (t("checkinGuest.sexMale") as string)
    : sex === "Female"
      ? (t("checkinGuest.sexFemale") as string)
      : sex;
  const nationality = guest.nationality
    ? (isEs ? guest.nationality.name_es : guest.nationality.name)
    : null;

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("guestDetailsDrawer.section.personal") as string}
        </div>
      </div>
      <div className={classes.kvGrid}>
        <Field
          label={t("checkinGuest.firstName") as string}
          value={guest.first_name}
        />
        <Field
          label={t("checkinGuest.lastName") as string}
          value={guest.last_name}
        />
        <Field
          label={t("checkinGuest.secondLastName") as string}
          value={guest.second_last_name}
        />
        <Field
          label={t("checkinGuest.dateOfBirth") as string}
          value={formatDateOfBirth(guest.date_of_birth)}
        />
        <Field
          label={t("checkinGuest.sex") as string}
          value={sexLabel ?? null}
        />
        <Field
          label={t("guestDetailsDrawer.field.relationship") as string}
          value={guest.relationship_code}
        />
        <Field
          label={t("checkinGuest.nationality") as string}
          value={nationality}
          span={2}
        />
      </div>
    </div>
  );
}

export function DocumentSection({ guest, sectionNum }: SectionProps) {
  const { t } = useTranslation();

  const docTypeKey = guest.document_type ? `checkinGuest.docType.${guest.document_type}` : null;
  const docTypeLabel = docTypeKey && t(docTypeKey) !== docTypeKey
    ? (t(docTypeKey) as string)
    : guest.document_type;

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("guestDetailsDrawer.section.document") as string}
        </div>
      </div>
      <div className={classes.kvGrid}>
        <Field
          label={t("checkinGuest.documentType") as string}
          value={docTypeLabel ?? null}
        />
        <Field
          label={t("guestDetailsDrawer.field.documentNumber") as string}
          value={guest.document_number}
          mono
        />
        <Field
          label={t("guestDetailsDrawer.field.supportNumber") as string}
          hint={t("guestDetailsDrawer.field.supportNumberHint") as string}
          value={guest.document_support_number}
          mono
          span={2}
        />
      </div>
    </div>
  );
}

export function ResidenceSection({ guest, sectionNum }: SectionProps) {
  const { t, i18n } = useTranslation();
  const isEs = i18n.language.startsWith("es");

  const country = guest.residence_country
    ? (isEs ? guest.residence_country.name_es : guest.residence_country.name)
    : null;
  const province = guest.residence_province?.name ?? null;
  const city = guest.residence_city?.name ?? guest.residence_city_name ?? null;

  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("guestDetailsDrawer.section.residence") as string}
        </div>
      </div>
      <div className={classes.kvGrid}>
        <Field
          label={t("guestDetailsDrawer.field.country") as string}
          value={country}
          span={2}
        />
        <Field
          label={t("guestDetailsDrawer.field.province") as string}
          value={province}
        />
        <Field
          label={t("guestDetailsDrawer.field.city") as string}
          value={city}
        />
        <Field
          label={t("guestDetailsDrawer.field.address") as string}
          value={guest.residence_address}
          span={2}
        />
        <Field
          label={t("guestDetailsDrawer.field.postalCode") as string}
          value={guest.residence_postal_code}
        />
      </div>
    </div>
  );
}

export function ContactSection({ guest, sectionNum }: SectionProps) {
  const { t, i18n } = useTranslation();
  const phoneEmpty = !guest.phone;

  // Three states: opted in (with consent date), unsubscribed after consenting,
  // or never opted in. Mirrors the mail icon on the /guests table.
  const consentDate = guest.marketing_consent_at
    ? new Date(guest.marketing_consent_at).toLocaleDateString(i18n.language, {
        year: "numeric",
        month: "short",
        day: "numeric",
      })
    : null;
  const emailConsentValue = guest.marketing_consent
    ? consentDate
      ? `${t("guestDetailsDrawer.field.emailConsentYes")} · ${consentDate}`
      : (t("guestDetailsDrawer.field.emailConsentYes") as string)
    : guest.marketing_consent_at
      ? (t("guestDetailsDrawer.field.emailConsentUnsubscribed") as string)
      : (t("guestDetailsDrawer.field.emailConsentNo") as string);
  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {t("guestDetailsDrawer.section.contact") as string}
        </div>
      </div>
      <div className={classes.kvGrid}>
        <Field
          label={t("guestDetailsDrawer.field.email") as string}
          value={guest.email}
          span={2}
        />
        <div className={`${classes.field} ${classes.span2}`}>
          <label className={classes.fieldLabel}>
            {t("guestDetailsDrawer.field.phone") as string}
          </label>
          <div className={classes.phoneField}>
            <span className="prefix">{guest.phone_prefix || "+"}</span>
            <div className="value" data-empty={phoneEmpty}>
              {phoneEmpty ? DASH : guest.phone}
            </div>
          </div>
        </div>
        <Field
          label={t("guestDetailsDrawer.field.emailConsent") as string}
          value={emailConsentValue}
          span={2}
        />
      </div>
    </div>
  );
}
