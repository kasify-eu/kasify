import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Button,
  Select,
  SimpleGrid,
  Stack,
  TextInput,
} from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { ApiClient, createApiConfig } from "~/lib/api-client";
import { DateSelect } from "~/components/date-select";
import { GeoLocationSelect } from "~/components/geo-location-select";
import { DIAL_CODES } from "~/lib/dial-codes";
import { validateDocumentSupportNumber } from "~/lib/document-support-number";
import type { Guest } from "~/types/bookings";
import type { GuestRegistrationRequest } from "~/types/checkin";
import { SPAIN_ALPHA3, type GeoCountry } from "~/types/geo";
import classes from "./guest-details-drawer.module.css";

const ADULT_AGE = 14;
const MINOR_AGE = 18;

// SES TIPO_PARENTESCO catalog. Kept in sync with the public checkin form
// (dashboard/app/routes/checkin/guest.tsx) and the backend allow-list in
// api/src/main/kotlin/model/api/requests/GuestRegistrationRequest.kt.
const PARENTESCO_CODES = [
  "AB",
  "BA",
  "BN",
  "CD",
  "CY",
  "HJ",
  "HR",
  "NI",
  "PM",
  "SB",
  "SG",
  "TI",
  "TU",
  "YN",
  "OT",
] as const;
const PERSON_NAME_REGEX = /^[\p{L} '\-.]+$/u;
const ISO_ALPHA3_REGEX = /^[A-Z]{3}$/;
const DNI_REGEX = /^\d{8}[A-HJ-NP-TV-Z]$/;
const NIE_REGEX = /^[XYZ]\d{7}[A-HJ-NP-TV-Z]$/;
const PASSPORT_REGEX = /^[A-Z0-9]{6,20}$/;
const OTHER_DOC_REGEX = /^[A-Z0-9-]{1,20}$/;
const SPANISH_POSTAL_CODE_REGEX = /^\d{5}$/;
const EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const PHONE_REGEX = /^[0-9 ()-]{6,20}$/;
const DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

function isValidDniOrNieChecksum(value: string): boolean {
  const v = value.toUpperCase();
  let digits: string;
  let expected: string;
  if (DNI_REGEX.test(v)) {
    digits = v.substring(0, 8);
    expected = v[v.length - 1];
  } else if (NIE_REGEX.test(v)) {
    const prefix = v[0] === "X" ? "0" : v[0] === "Y" ? "1" : "2";
    digits = prefix + v.substring(1, 8);
    expected = v[v.length - 1];
  } else {
    return false;
  }
  return DNI_LETTERS[Number(digits) % 23] === expected;
}

function getAge(dob: Date | null): number | null {
  if (!dob) return null;
  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const m = today.getMonth() - dob.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) age -= 1;
  return age;
}

function toIsoDate(d: Date | null): string | null {
  if (!d) return null;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")}`;
}

function parseIsoDate(s: string | null | undefined): Date | null {
  if (!s) return null;
  const [y, m, d] = s.split("-").map(Number);
  if (!y || !m || !d) return null;
  const date = new Date(y, m - 1, d);
  return isNaN(date.getTime()) ? null : date;
}

interface FormState {
  nationality: string;
  firstName: string;
  lastName: string;
  secondLastName: string;
  dateOfBirth: Date | null;
  sex: string;
  documentType: string;
  documentNumber: string;
  documentSupportNumber: string;
  residenceAddress: string;
  residenceCountry: string;
  residenceProvinceId: string;
  residenceCityId: string;
  residenceCityText: string;
  residencePostalCode: string;
  phonePrefix: string;
  phone: string;
  email: string;
  relationshipCode: string;
}

const EMPTY_FORM: FormState = {
  nationality: "",
  firstName: "",
  lastName: "",
  secondLastName: "",
  dateOfBirth: null,
  sex: "",
  documentType: "",
  documentNumber: "",
  documentSupportNumber: "",
  residenceAddress: "",
  residenceCountry: "",
  residenceProvinceId: "",
  residenceCityId: "",
  residenceCityText: "",
  residencePostalCode: "",
  phonePrefix: "",
  phone: "",
  email: "",
  relationshipCode: "",
};

function fromGuest(guest: Guest): FormState {
  return {
    nationality: guest.nationality?.alpha_3 ?? "",
    firstName: guest.first_name ?? "",
    lastName: guest.last_name ?? "",
    secondLastName: guest.second_last_name ?? "",
    dateOfBirth: parseIsoDate(guest.date_of_birth),
    sex: guest.sex ?? "",
    documentType: guest.document_type ?? "",
    documentNumber: guest.document_number ?? "",
    documentSupportNumber: guest.document_support_number ?? "",
    residenceAddress: guest.residence_address ?? "",
    residenceCountry: guest.residence_country?.alpha_3 ?? "",
    residenceProvinceId: guest.residence_province?.id ?? "",
    residenceCityId: guest.residence_city?.id ?? "",
    residenceCityText: guest.residence_city_name ?? guest.residence_city?.name ?? "",
    residencePostalCode: guest.residence_postal_code ?? "",
    phonePrefix: guest.phone_prefix ?? "",
    phone: guest.phone ?? "",
    email: guest.email ?? "",
    relationshipCode: guest.relationship_code ?? "",
  };
}

export interface GuestEditFormProps {
  /** Existing guest values when editing; null when creating. */
  guest: Guest | null;
  loading?: boolean;
  submitError?: string | null;
  onSubmit: (data: GuestRegistrationRequest) => void;
  onCancel: () => void;
  /**
   * True when the booking the guest belongs to reports any children/infants.
   * Triggers the SES "parentesco" Select for adult guests (>= 18), which the
   * backend requires in that branch.
   */
  bookingHasMinors?: boolean;
}

export function GuestEditForm({
  guest,
  loading = false,
  submitError,
  onSubmit,
  onCancel,
  bookingHasMinors = false,
}: GuestEditFormProps) {
  const { t, i18n } = useTranslation();
  const [form, setForm] = useState<FormState>(() => (guest ? fromGuest(guest) : EMPTY_FORM));
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({});
  const [geoCountries, setGeoCountries] = useState<GeoCountry[]>([]);

  useEffect(() => {
    const client = ApiClient.createFromBrowser(createApiConfig());
    client
      .get<{ countries: GeoCountry[] }>("/geo/countries")
      .then((res) => setGeoCountries(res.data.countries))
      .catch(() => {});
  }, []);

  const countries = useMemo(() => {
    const isEs = i18n.language.startsWith("es");
    return geoCountries
      .map((c) => ({ value: c.alpha_3, label: isEs ? c.name_es : c.name }))
      .sort((a, b) => a.label.localeCompare(b.label, isEs ? "es" : "en"));
  }, [geoCountries, i18n.language]);

  const phonePrefixOptions = useMemo(() => {
    const isEs = i18n.language.startsWith("es");
    const seen = new Set<string>();
    const options: { value: string; label: string }[] = [];
    for (const c of geoCountries) {
      const code = DIAL_CODES[c.alpha_3];
      if (!code || seen.has(code)) continue;
      seen.add(code);
      options.push({
        value: code,
        label: `${code} (${isEs ? c.name_es : c.name})`,
      });
    }
    options.sort((a, b) => a.label.localeCompare(b.label));
    return options;
  }, [geoCountries, i18n.language]);

  const age = getAge(form.dateOfBirth);
  const isAdult = age === null || age >= ADULT_AGE;
  // Parentesco asked only from adults (>= 18) when the booking has any minor.
  const requiresParentesco = bookingHasMinors && age !== null && age >= MINOR_AGE;
  const isSpanish = form.nationality === SPAIN_ALPHA3;
  const isSpanishDoc = form.documentType === "DNI" || form.documentType === "NIE";
  const isSpainResident = form.residenceCountry === SPAIN_ALPHA3;

  function update<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  }

  function validate(): boolean {
    const next: Partial<Record<keyof FormState, string>> = {};

    if (!form.firstName.trim() || !PERSON_NAME_REGEX.test(form.firstName.trim())) {
      next.firstName = t("checkinGuest.required");
    }
    if (!form.lastName.trim() || !PERSON_NAME_REGEX.test(form.lastName.trim())) {
      next.lastName = t("checkinGuest.required");
    }
    if (isSpanish && !form.secondLastName.trim()) {
      next.secondLastName = t("checkinGuest.required");
    } else if (
      form.secondLastName.trim() &&
      !PERSON_NAME_REGEX.test(form.secondLastName.trim())
    ) {
      next.secondLastName = t("checkinGuest.required");
    }
    if (!form.nationality || !ISO_ALPHA3_REGEX.test(form.nationality)) {
      next.nationality = t("checkinGuest.invalidNationality");
    }
    if (!form.dateOfBirth) {
      next.dateOfBirth = t("checkinGuest.required");
    } else if (form.dateOfBirth >= new Date()) {
      next.dateOfBirth = t("checkinGuest.invalidDate");
    }
    if (!form.sex) next.sex = t("checkinGuest.required");

    if (isAdult) {
      if (!form.documentType) next.documentType = t("checkinGuest.required");
      if (!form.documentNumber.trim()) {
        next.documentNumber = t("checkinGuest.required");
      } else if (form.documentType) {
        const normalized = form.documentNumber.trim().toUpperCase();
        if (form.documentType === "DNI") {
          if (!DNI_REGEX.test(normalized) || !isValidDniOrNieChecksum(normalized)) {
            next.documentNumber = t("checkinGuest.invalidDni");
          }
        } else if (form.documentType === "NIE") {
          if (!NIE_REGEX.test(normalized) || !isValidDniOrNieChecksum(normalized)) {
            next.documentNumber = t("checkinGuest.invalidNie");
          }
        } else if (form.documentType === "Passport") {
          if (!PASSPORT_REGEX.test(normalized)) {
            next.documentNumber = t("checkinGuest.invalidPassport");
          }
        } else if (form.documentType === "Other") {
          if (!OTHER_DOC_REGEX.test(normalized)) {
            next.documentNumber = t("checkinGuest.invalidDocument");
          }
        }
      }
      if (isSpanishDoc) {
        const supportErrorKey = validateDocumentSupportNumber(
          form.documentType,
          form.documentSupportNumber,
          form.documentNumber,
        );
        if (supportErrorKey) {
          next.documentSupportNumber = t(supportErrorKey);
        }
      }
    }

    // --- Contact (mandatory for everyone, including minors: at least phone or email) ---
    if (!form.phone.trim() && !form.email.trim()) {
      next.phone = t("checkinGuest.contactRequired");
      next.email = t("checkinGuest.contactRequired");
    }
    if (form.email.trim() && !EMAIL_REGEX.test(form.email.trim())) {
      next.email = t("checkinGuest.invalidEmail");
    }
    if (form.phone.trim() && !PHONE_REGEX.test(form.phone.trim())) {
      next.phone = t("checkinGuest.invalidPhone");
    }

    // --- Address (mandatory for everyone, including minors) ---
    if (!form.residenceAddress.trim()) next.residenceAddress = t("checkinGuest.required");
    if (!form.residenceCountry || !ISO_ALPHA3_REGEX.test(form.residenceCountry)) {
      next.residenceCountry = t("checkinGuest.invalidCountry");
    }
    if (isSpainResident && !form.residenceProvinceId) {
      next.residenceProvinceId = t("checkinGuest.required");
    }
    if (isSpainResident) {
      if (!form.residenceCityId) next.residenceCityId = t("checkinGuest.required");
    } else if (!form.residenceCityText.trim()) {
      next.residenceCityText = t("checkinGuest.required");
    }
    if (!form.residencePostalCode.trim()) {
      next.residencePostalCode = t("checkinGuest.required");
    } else if (
      isSpainResident &&
      !SPANISH_POSTAL_CODE_REGEX.test(form.residencePostalCode.trim())
    ) {
      next.residencePostalCode = t("checkinGuest.invalidPostalCodeSpain");
    }

    if (requiresParentesco && !form.relationshipCode) {
      next.relationshipCode = t("checkinGuest.required");
    }

    setErrors(next);
    return Object.keys(next).length === 0;
  }

  function buildPayload(): GuestRegistrationRequest | null {
    const isoDob = toIsoDate(form.dateOfBirth);
    if (!isoDob) return null;
    return {
      first_name: form.firstName.trim(),
      last_name: form.lastName.trim(),
      second_last_name: form.secondLastName.trim() || null,
      nationality_country_code: form.nationality,
      date_of_birth: isoDob,
      sex: form.sex as "Male" | "Female",
      document_type:
        isAdult && form.documentType
          ? (form.documentType as "DNI" | "NIE" | "Passport" | "Other")
          : null,
      document_number:
        isAdult && form.documentNumber.trim() ? form.documentNumber.trim().toUpperCase() : null,
      document_support_number:
        isAdult && form.documentSupportNumber.trim() ? form.documentSupportNumber.trim() : null,
      residence_address: form.residenceAddress.trim() || null,
      residence_country_code: form.residenceCountry || null,
      residence_province_id:
        isSpainResident && form.residenceProvinceId ? form.residenceProvinceId : null,
      residence_city_id:
        isSpainResident && form.residenceCityId ? form.residenceCityId : null,
      residence_city:
        !isSpainResident && form.residenceCityText.trim()
          ? form.residenceCityText.trim()
          : null,
      residence_postal_code: form.residencePostalCode.trim() || null,
      phone_prefix: form.phonePrefix || null,
      phone: form.phone.trim() || null,
      email: form.email.trim() || null,
      relationship_code: requiresParentesco && form.relationshipCode
        ? form.relationshipCode
        : null,
    };
  }

  function handleSubmit() {
    if (!validate()) return;
    const payload = buildPayload();
    if (!payload) return;
    onSubmit(payload);
  }

  const documentTypeOptions = [
    { value: "DNI", label: "DNI / NIF" },
    { value: "NIE", label: "NIE" },
    { value: "Passport", label: t("checkinGuest.docType.Passport") as string },
    { value: "Other", label: t("checkinGuest.docType.Other") as string },
  ];

  const sexOptions = [
    { value: "Male", label: t("checkinGuest.sexMale") as string },
    { value: "Female", label: t("checkinGuest.sexFemale") as string },
  ];

  return (
    <div className={classes.formContainer}>
      <div className={classes.formBody}>
        <Stack gap="lg">
          {submitError && (
            <Alert color="red" icon={<IconAlertCircle size={16} />}>
              {submitError}
            </Alert>
          )}

        <Section sectionNum="01" title={t("guestDetailsDrawer.section.personal") as string}>
          <Stack gap="md">
            <Select
              label={t("checkinGuest.nationality") as string}
              data={countries}
              searchable
              value={form.nationality}
              onChange={(v) => {
                const code = v ?? "";
                update("nationality", code);
                if (code) {
                  if (DIAL_CODES[code] && !form.phonePrefix) {
                    update("phonePrefix", DIAL_CODES[code]);
                  }
                  if (!form.residenceCountry) update("residenceCountry", code);
                  if (code === SPAIN_ALPHA3 && !form.documentType) {
                    update("documentType", "DNI");
                  }
                }
              }}
              error={errors.nationality}
            />
            <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
              <TextInput
                label={t("checkinGuest.firstName") as string}
                value={form.firstName}
                onChange={(e) => update("firstName", e.currentTarget.value)}
                error={errors.firstName}
              />
              <TextInput
                label={t("checkinGuest.lastName") as string}
                value={form.lastName}
                onChange={(e) => update("lastName", e.currentTarget.value)}
                error={errors.lastName}
              />
            </SimpleGrid>
            {isSpanish && (
              <TextInput
                label={t("checkinGuest.secondLastName") as string}
                value={form.secondLastName}
                onChange={(e) => update("secondLastName", e.currentTarget.value)}
                error={errors.secondLastName}
              />
            )}
            <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
              <DateSelect
                label={t("checkinGuest.dateOfBirth") as string}
                value={form.dateOfBirth}
                onChange={(d) => update("dateOfBirth", d)}
                error={errors.dateOfBirth}
                maxDate={new Date()}
              />
              <Select
                label={t("checkinGuest.sex") as string}
                data={sexOptions}
                value={form.sex}
                onChange={(v) => update("sex", v ?? "")}
                error={errors.sex}
              />
            </SimpleGrid>
          </Stack>
        </Section>

        {isAdult && (
          <Section sectionNum="02" title={t("guestDetailsDrawer.section.document") as string}>
            <Stack gap="md">
              <Select
                label={t("checkinGuest.documentType") as string}
                data={documentTypeOptions}
                value={form.documentType}
                onChange={(v) => update("documentType", v ?? "")}
                error={errors.documentType}
              />
              <TextInput
                label={t("guestDetailsDrawer.field.documentNumber") as string}
                value={form.documentNumber}
                onChange={(e) =>
                  update("documentNumber", e.currentTarget.value.toUpperCase())
                }
                error={errors.documentNumber}
                styles={{ input: { fontFamily: "JetBrains Mono, monospace" } }}
              />
              {isSpanishDoc && (
                <TextInput
                  label={t("guestDetailsDrawer.field.supportNumber") as string}
                  description={t("guestDetailsDrawer.field.supportNumberHint") as string}
                  value={form.documentSupportNumber}
                  onChange={(e) => update("documentSupportNumber", e.currentTarget.value)}
                  error={errors.documentSupportNumber}
                  styles={{ input: { fontFamily: "JetBrains Mono, monospace" } }}
                />
              )}
            </Stack>
          </Section>
        )}

        {/* Residence — required for everyone, including minors */}
        <Section sectionNum="03" title={t("guestDetailsDrawer.section.residence") as string}>
          <Stack gap="md">
            <GeoLocationSelect
              countryCode={form.residenceCountry || null}
              provinceId={form.residenceProvinceId || null}
              cityId={form.residenceCityId || null}
              cityText={form.residenceCityText}
              onChangeCountry={(code) => {
                update("residenceCountry", code);
                update("residenceProvinceId", "");
                update("residenceCityId", "");
                update("residenceCityText", "");
              }}
              onChangeProvince={(id) => update("residenceProvinceId", id ?? "")}
              onChangeCity={(id) => update("residenceCityId", id ?? "")}
              onChangeCityText={(text) => update("residenceCityText", text)}
              required
              errors={{
                country: errors.residenceCountry,
                province: errors.residenceProvinceId,
                city: errors.residenceCityId ?? errors.residenceCityText,
              }}
              labels={{
                country: t("guestDetailsDrawer.field.country") as string,
                countryPlaceholder: t("guestDetailsDrawer.field.country") as string,
                province: t("guestDetailsDrawer.field.province") as string,
                provincePlaceholder: t("guestDetailsDrawer.field.province") as string,
                city: t("guestDetailsDrawer.field.city") as string,
                cityPlaceholder: t("guestDetailsDrawer.field.city") as string,
              }}
            />
            <TextInput
              label={t("guestDetailsDrawer.field.address") as string}
              value={form.residenceAddress}
              onChange={(e) => update("residenceAddress", e.currentTarget.value)}
              error={errors.residenceAddress}
            />
            <TextInput
              label={t("guestDetailsDrawer.field.postalCode") as string}
              value={form.residencePostalCode}
              onChange={(e) => update("residencePostalCode", e.currentTarget.value)}
              error={errors.residencePostalCode}
            />
          </Stack>
        </Section>

        {/* Contact — required for everyone, including minors (SES rule).
            A minor without their own phone/email should carry a parent's. */}
        <Section sectionNum="04" title={t("guestDetailsDrawer.section.contact") as string}>
          <Stack gap="md">
            <TextInput
              label={t("guestDetailsDrawer.field.email") as string}
              value={form.email}
              onChange={(e) => update("email", e.currentTarget.value)}
              error={errors.email}
            />
            <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
              <Select
                label={t("guestDetailsDrawer.field.phonePrefix") as string}
                data={phonePrefixOptions}
                searchable
                value={form.phonePrefix}
                onChange={(v) => update("phonePrefix", v ?? "")}
              />
              <TextInput
                label={t("guestDetailsDrawer.field.phone") as string}
                value={form.phone}
                onChange={(e) => update("phone", e.currentTarget.value)}
                error={errors.phone}
              />
            </SimpleGrid>
          </Stack>
        </Section>

        {requiresParentesco && (
          <Section sectionNum="05" title={t("checkinGuest.parentesco.title") as string}>
            <Select
              label={t("checkinGuest.parentesco.label") as string}
              placeholder={t("checkinGuest.parentesco.placeholder") as string}
              description={t("checkinGuest.parentesco.hint") as string}
              data={PARENTESCO_CODES.map((code) => ({
                value: code,
                label: t(`checkinGuest.parentesco.code.${code}`) as string,
              }))}
              value={form.relationshipCode}
              onChange={(v) => update("relationshipCode", v ?? "")}
              error={errors.relationshipCode}
            />
          </Section>
        )}

        </Stack>
      </div>
      <div className={classes.formFooter}>
        <Button variant="default" onClick={onCancel} disabled={loading}>
          {t("guestDetailsDrawer.cancel") as string}
        </Button>
        <Button onClick={handleSubmit} loading={loading}>
          {t("guestDetailsDrawer.save") as string}
        </Button>
      </div>
    </div>
  );
}

function Section({
  sectionNum,
  title,
  children,
}: {
  sectionNum: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className={classes.section}>
      <div className={classes.sectionHead}>
        <div className={classes.sectionLabel}>
          <span className={classes.num}>{sectionNum}</span>
          {title}
        </div>
      </div>
      {children}
    </div>
  );
}
