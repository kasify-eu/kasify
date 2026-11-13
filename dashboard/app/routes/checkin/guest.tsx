import {
  Anchor,
  Box,
  Button,
  Checkbox,
  Divider,
  Group,
  Image,
  Modal,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  ThemeIcon,
  Title,
} from "@mantine/core";
import { getLegalUrl } from "~/lib/public-url";
import { DateSelect } from "~/components/date-select";
import {
  IconAlertCircle,
  IconArrowLeft,
  IconBadge,
  IconCircleCheckFilled,
  IconGavel,
  IconHome,
  IconLink,
  IconMapPin,
  IconPhone,
  IconRosetteDiscountCheckFilled,
  IconSignature,
  IconSparkles,
  IconStack2,
  IconUser,
  IconUserPlus,
} from "@tabler/icons-react";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { useEffect, useMemo, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { redirect, useLoaderData, useNavigate } from "react-router";
import { ApiClient, ApiClientError, createApiConfig } from "~/lib/api-client";
import { DIAL_CODES } from "~/lib/dial-codes";
import { validateDocumentSupportNumber } from "~/lib/document-support-number";
import type { GuestRegistrationRequest } from "~/types/checkin";
import type { GeoCountry } from "~/types/geo";
import { SPAIN_ALPHA3 } from "~/types/geo";
import { GeoLocationSelect } from "~/components/geo-location-select";
import { KeyboardSelect } from "~/components/keyboard-select";
import type { Route } from "./+types/guest";

// SES document types - aligned with backend DocumentType enum
const DOCUMENT_TYPES = [
  { value: "DNI", label: "DNI / NIF" },
  { value: "NIE", label: "NIE" },
  { value: "Passport", label: "Passport" },
  { value: "Other", label: "Other" },
] as const;

const SEX_OPTIONS = [
  { value: "Male", label: "Male" },
  { value: "Female", label: "Female" },
] as const;

// Mirrors backend SES Hospedajes regexes (see GuestRegistrationRequest.kt).
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
const ADULT_AGE = 14;
// Age cutoff used for "parentesco" — minors (< 18) must declare their
// relationship to an accompanying adult in the booking.
const MINOR_AGE = 18;

// SES TIPO_PARENTESCO codes shown to a minor — only roles a minor can
// plausibly hold relative to an accompanying adult. Backend still accepts the
// full SES catalog (see api/src/main/kotlin/model/api/requests/GuestRegistrationRequest.kt).
const PARENTESCO_CODES = [
  "HJ", // Hijo/a
  "NI", // Nieto/a
  "HR", // Hermano/a
  "SB", // Sobrino/a
  "BN", // Bisnieto/a
  "OT", // Otro
] as const;

function expectedDniLetter(digits: string): string {
  return DNI_LETTERS[Number(digits) % 23];
}

function isValidDniOrNieChecksum(value: string): boolean {
  const normalized = value.toUpperCase();
  let digits: string;
  let expected: string;
  if (DNI_REGEX.test(normalized)) {
    digits = normalized.substring(0, 8);
    expected = normalized[normalized.length - 1];
  } else if (NIE_REGEX.test(normalized)) {
    const prefix =
      normalized[0] === "X" ? "0" : normalized[0] === "Y" ? "1" : "2";
    digits = prefix + normalized.substring(1, 8);
    expected = normalized[normalized.length - 1];
  } else {
    return false;
  }
  return expectedDniLetter(digits) === expected;
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
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

const normalizeSearch = (s: string) =>
  s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();

const unaccentFilter: import("@mantine/core").OptionsFilter = ({ options, search }) => {
  const q = normalizeSearch(search);
  return options.filter((o) =>
    "label" in o && typeof o.label === "string"
      ? normalizeSearch(o.label).includes(q)
      : false,
  );
};

interface GuestFormData {
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
  marketingConsent: boolean;
}

const INITIAL_FORM: GuestFormData = {
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
  marketingConsent: false,
};

export function meta(_: Route.MetaArgs) {
  return [{ title: `Guest Registration` }];
}

export async function loader({ params, request }: Route.LoaderArgs) {
  const bookingCode = params.bookingCode;

  if (!bookingCode) {
    return {
      bookingCode: null,
      totalGuests: 0,
      registeredCount: 0,
      propertyName: null,
      hasMinors: false,
      error: "notFound" as const,
    };
  }

  try {
    const client = ApiClient.createPublic(request, createApiConfig());
    const response = await client.getCheckin(bookingCode);
    const checkin = response.data;

    // If checkin is closed or all guests registered, send them back to the welcome page.
    if (checkin.checkin_completed || checkin.checkin_closed) {
      throw redirect(`/checkin/${bookingCode}`);
    }

    return {
      bookingCode,
      totalGuests: checkin.guests_number,
      registeredCount: checkin.guests.length,
      propertyName: checkin.property.name,
      hasMinors: checkin.has_minors,
      error: null,
    };
  } catch (err) {
    if (err instanceof Response) throw err; // let redirects through
    if (err instanceof ApiClientError && err.status === 404) {
      return {
        bookingCode: null,
        totalGuests: 0,
        registeredCount: 0,
        propertyName: null,
        hasMinors: false,
        error: "notFound" as const,
      };
    }
    return {
      bookingCode: null,
      totalGuests: 0,
      registeredCount: 0,
      propertyName: null,
      hasMinors: false,
      error: "loadFailed" as const,
    };
  }
}

function SectionHeader({
  icon,
  title,
}: {
  icon: React.ReactNode;
  title: string;
}) {
  return (
    <Group gap="xs" mb="md">
      {icon}
      <Title
        order={3}
        style={{ color: "var(--mantine-color-blue-7)" }}
      >
        {title}
      </Title>
    </Group>
  );
}

export default function GuestRegistrationPage() {
  const { bookingCode, totalGuests, registeredCount, propertyName, error } =
    useLoaderData<typeof loader>();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [form, setForm] = useState<GuestFormData>(INITIAL_FORM);
  const [errors, setErrors] = useState<Partial<Record<keyof GuestFormData, string>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [successOpen, setSuccessOpen] = useState(false);
  const [validationErrorsOpen, setValidationErrorsOpen] = useState(false);
  const [supportNumberHelpOpen, setSupportNumberHelpOpen] = useState(false);
  const [successContext, setSuccessContext] = useState<{
    firstName: string;
    checkinCompleted: boolean;
  } | null>(null);

  const [geoCountries, setGeoCountries] = useState<GeoCountry[]>([]);

  // The guest currently being registered is the next one after those already saved.
  const guestNumber = Math.min(registeredCount + 1, totalGuests);

  // Dynamic flags driving validation and section visibility (matches backend SES rules).
  const age = getAge(form.dateOfBirth);
  // Treat unknown age as adult so the form requests data until DOB is filled.
  const isAdult = age === null || age >= ADULT_AGE;
  // SES "parentesco": stored on the minor's record, declaring their
  // relationship to an accompanying adult in the booking.
  const requiresParentesco = age !== null && age < MINOR_AGE;
  // Marketing opt-in is only offered to adults (18+); the backend re-checks
  // this and additionally requires an email before recording consent.
  const isMarketingEligible = age !== null && age >= MINOR_AGE;
  const isSpanish = form.nationality === SPAIN_ALPHA3;
  const isSpanishDoc = form.documentType === "DNI" || form.documentType === "NIE";
  const isSpainResident = form.residenceCountry === SPAIN_ALPHA3;

  // Load countries from geo API (used for nationality select + phone prefix options)
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

  const updateField = <K extends keyof GuestFormData>(
    field: K,
    value: GuestFormData[K],
  ) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    }
  };

  const validate = (): boolean => {
    const newErrors: Partial<Record<keyof GuestFormData, string>> = {};

    // --- Identity (required for everyone, including minors) ---
    if (!form.firstName.trim()) {
      newErrors.firstName = t("checkinGuest.required");
    } else if (!PERSON_NAME_REGEX.test(form.firstName.trim())) {
      newErrors.firstName = t("checkinGuest.required");
    }
    if (!form.lastName.trim()) {
      newErrors.lastName = t("checkinGuest.required");
    } else if (!PERSON_NAME_REGEX.test(form.lastName.trim())) {
      newErrors.lastName = t("checkinGuest.required");
    }

    // Spanish nationals must provide a second surname (typical legal name format).
    if (isSpanish && !form.secondLastName.trim()) {
      newErrors.secondLastName = t("checkinGuest.required");
    } else if (
      form.secondLastName.trim() &&
      !PERSON_NAME_REGEX.test(form.secondLastName.trim())
    ) {
      newErrors.secondLastName = t("checkinGuest.required");
    }

    if (!form.nationality || !ISO_ALPHA3_REGEX.test(form.nationality)) {
      newErrors.nationality = t("checkinGuest.invalidNationality");
    }

    if (!form.dateOfBirth) {
      newErrors.dateOfBirth = t("checkinGuest.required");
    } else if (form.dateOfBirth >= new Date()) {
      newErrors.dateOfBirth = t("checkinGuest.invalidDate");
    }

    if (!form.sex) newErrors.sex = t("checkinGuest.required");

    // --- Document / Contact only enforced for adults (>=14) ---
    if (isAdult) {
      if (!form.documentType) {
        newErrors.documentType = t("checkinGuest.required");
      }
      if (!form.documentNumber.trim()) {
        newErrors.documentNumber = t("checkinGuest.required");
      } else if (form.documentType) {
        const normalized = form.documentNumber.trim().toUpperCase();
        if (form.documentType === "DNI") {
          if (!DNI_REGEX.test(normalized) || !isValidDniOrNieChecksum(normalized)) {
            newErrors.documentNumber = t("checkinGuest.invalidDni");
          }
        } else if (form.documentType === "NIE") {
          if (!NIE_REGEX.test(normalized) || !isValidDniOrNieChecksum(normalized)) {
            newErrors.documentNumber = t("checkinGuest.invalidNie");
          }
        } else if (form.documentType === "Passport") {
          if (!PASSPORT_REGEX.test(normalized)) {
            newErrors.documentNumber = t("checkinGuest.invalidPassport");
          }
        } else if (form.documentType === "Other") {
          if (!OTHER_DOC_REGEX.test(normalized)) {
            newErrors.documentNumber = t("checkinGuest.invalidDocument");
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
          newErrors.documentSupportNumber = t(supportErrorKey);
        }
      }
    }

    // --- Contact (mandatory for everyone, including minors) ---
    // SES requires at least a phone or email on every traveler of a parte de viajeros.
    if (!form.phone.trim() && !form.email.trim()) {
      newErrors.phone = t("checkinGuest.contactRequired");
      newErrors.email = t("checkinGuest.contactRequired");
    }
    if (form.email.trim() && !EMAIL_REGEX.test(form.email.trim())) {
      newErrors.email = t("checkinGuest.invalidEmail");
    }
    if (form.phone.trim() && !PHONE_REGEX.test(form.phone.trim())) {
      newErrors.phone = t("checkinGuest.invalidPhone");
    }

    // --- Address (mandatory for everyone, including minors) ---
    if (!form.residenceAddress.trim()) {
      newErrors.residenceAddress = t("checkinGuest.required");
    }
    if (!form.residenceCountry || !ISO_ALPHA3_REGEX.test(form.residenceCountry)) {
      newErrors.residenceCountry = t("checkinGuest.invalidCountry");
    }
    if (isSpainResident && !form.residenceProvinceId) {
      newErrors.residenceProvinceId = t("checkinGuest.required");
    }
    if (isSpainResident) {
      if (!form.residenceCityId) {
        newErrors.residenceCityId = t("checkinGuest.required");
      }
    } else if (!form.residenceCityText.trim()) {
      newErrors.residenceCityText = t("checkinGuest.required");
    }
    if (!form.residencePostalCode.trim()) {
      newErrors.residencePostalCode = t("checkinGuest.required");
    } else if (
      isSpainResident &&
      !SPANISH_POSTAL_CODE_REGEX.test(form.residencePostalCode.trim())
    ) {
      newErrors.residencePostalCode = t("checkinGuest.invalidPostalCodeSpain");
    }

    // --- Parentesco (only for adults >= 18 when the booking has minors) ---
    if (requiresParentesco && !form.relationshipCode) {
      newErrors.relationshipCode = t("checkinGuest.required");
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async () => {
    setSubmitError(null);
    if (!validate()) {
      setValidationErrorsOpen(true);
      return;
    }
    const isoDob = toIsoDate(form.dateOfBirth);
    if (!bookingCode || !isoDob) return;

    const payload: GuestRegistrationRequest = {
      first_name: form.firstName.trim(),
      last_name: form.lastName.trim(),
      second_last_name: form.secondLastName.trim() || null,
      nationality_country_code: form.nationality,
      date_of_birth: isoDob,
      sex: form.sex as "Male" | "Female",
      document_type: isAdult && form.documentType
        ? (form.documentType as "DNI" | "NIE" | "Passport" | "Other")
        : null,
      document_number: isAdult && form.documentNumber.trim()
        ? form.documentNumber.trim().toUpperCase()
        : null,
      document_support_number: isAdult && form.documentSupportNumber.trim()
        ? form.documentSupportNumber.trim()
        : null,
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
      marketing_consent:
        isMarketingEligible && form.marketingConsent && !!form.email.trim(),
    };

    const firstName = form.firstName.trim();

    setSubmitting(true);
    try {
      const client = ApiClient.createFromBrowser(createApiConfig());
      const result = await client.registerGuest(bookingCode, payload);
      setSuccessContext({ firstName, checkinCompleted: result.data.checkin_completed });
      setSuccessOpen(true);
    } catch (err) {
      const errorCode =
        err instanceof ApiClientError && err.code
          ? err.code
          : null;
      const key = errorCode ? `checkinGuest.error.${errorCode}` : null;
      const translated = key && t(key) !== key ? t(key) : t("checkinGuest.submitError");
      setSubmitError(translated);
    } finally {
      setSubmitting(false);
    }
  };

  const handleRegisterNextGuest = () => {
    setSuccessOpen(false);
    setSuccessContext(null);
    // Preserve fields commonly shared across guests in the same booking
    // (nationality, phone prefix, full residence address) so the next guest
    // only needs to fill in personal info.
    setForm((prev) => ({
      ...INITIAL_FORM,
      nationality: prev.nationality,
      phonePrefix: prev.phonePrefix,
      residenceCountry: prev.residenceCountry,
      residenceProvinceId: prev.residenceProvinceId,
      residenceCityId: prev.residenceCityId,
      residenceCityText: prev.residenceCityText,
      residenceAddress: prev.residenceAddress,
      residencePostalCode: prev.residencePostalCode,
    }));
    setErrors({});
    // Revalidate the loader so registeredCount advances and the step counter updates.
    navigate(".", { replace: true });
  };

  const handleBackToCheckin = () => {
    setSuccessOpen(false);
    setSuccessContext(null);
    navigate(`/checkin/${bookingCode}`);
  };

  const handleCopyFormLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      showSuccessToast(t("checkinGuest.linkCopied"));
    } catch {
      showErrorToast(t("checkinGuest.linkCopyFailed"));
    }
  };

  if (error) {
    return (
      <Stack align="center" gap="lg" py={80} px="md">
        <Title order={3}>{t("checkin.notFound")}</Title>
      </Stack>
    );
  }

  return (
    <>
        {/* Header */}
        <Box
          px="md"
          py="lg"
          style={{ borderBottom: "1px solid var(--mantine-color-gray-2)" }}
          bg="white"
        >
          <Group gap={8}>
            <IconArrowLeft
              size={20}
              style={{ cursor: "pointer" }}
              onClick={() => navigate(`/checkin/${bookingCode}`)}
            />
            <Box
              style={{
                width: 28,
                height: 28,
                backgroundColor: "#0F172A",
                borderRadius: 6,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <IconStack2 size={16} color="white" />
            </Box>
            <Text fw={700} size="sm" style={{ letterSpacing: "-0.02em" }}>
              {propertyName}
            </Text>
          </Group>
        </Box>

        {/* Content */}
        <Box px="md" py="lg" style={{ flex: 1 }}>
          <Stack gap="lg">
            {/* Step indicator + Title */}
            <Box>
              <Text
                size="xs"
                fw={700}
                c="blue.7"
                tt="uppercase"
                style={{ letterSpacing: "0.08em" }}
              >
                {t("checkinGuest.step", {
                  current: guestNumber,
                  total: totalGuests,
                })}
              </Text>
              <Title
                order={2}
                mt={4}
                style={{ letterSpacing: "-0.02em" }}
              >
                {t("checkinGuest.title")}
              </Title>
            </Box>

            {/* Personal Information */}
            <Paper
              p={{ base: 0, xs: "lg" }}
              radius="md"
              bg={{ base: "transparent", xs: "gray.0" }}
              bd={{ base: 0, xs: "1px solid var(--mantine-color-gray-3)" }}
            >
              <SectionHeader
                icon={<IconUser size={20} color="var(--mantine-color-blue-7)" />}
                title={t("checkinGuest.personalInfo")}
              />
              <Stack gap="md">
                <KeyboardSelect
                  label={t("checkinGuest.nationality")}
                  placeholder={t("checkinGuest.nationalityPlaceholder")}
                  data={countries}
                  filter={unaccentFilter}
                  value={form.nationality}
                  onChange={(v) => {
                    const code = v ?? "";
                    updateField("nationality", code);
                    if (code) {
                      if (DIAL_CODES[code] && !form.phonePrefix) {
                        updateField("phonePrefix", DIAL_CODES[code]);
                      }
                      if (!form.residenceCountry) {
                        updateField("residenceCountry", code);
                      }
                      if (code === "ESP" && !form.documentType) {
                        updateField("documentType", "DNI");
                      }
                    }
                  }}
                  error={errors.nationality}
                />
                <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
                  <TextInput
                    label={t("checkinGuest.firstName")}
                    placeholder={t("checkinGuest.firstNamePlaceholder")}
                    value={form.firstName}
                    onChange={(e) => updateField("firstName", e.currentTarget.value)}
                    error={errors.firstName}
                  />
                  <TextInput
                    label={t("checkinGuest.lastName")}
                    placeholder={t("checkinGuest.lastNamePlaceholder")}
                    value={form.lastName}
                    onChange={(e) => updateField("lastName", e.currentTarget.value)}
                    error={errors.lastName}
                  />
                </SimpleGrid>
                {(isSpanish || isSpanishDoc) && (
                  <TextInput
                    label={t("checkinGuest.secondLastName")}
                    placeholder={t("checkinGuest.secondLastNamePlaceholder")}
                    value={form.secondLastName}
                    onChange={(e) =>
                      updateField("secondLastName", e.currentTarget.value)
                    }
                    error={errors.secondLastName}
                  />
                )}
                <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
                  <DateSelect
                    label={t("checkinGuest.dateOfBirth")}
                    value={form.dateOfBirth}
                    onChange={(v) => updateField("dateOfBirth", v)}
                    error={errors.dateOfBirth}
                    maxDate={new Date()}
                  />
                  <KeyboardSelect
                    label={t("checkinGuest.sex")}
                    placeholder={t("checkinGuest.sexPlaceholder")}
                    data={SEX_OPTIONS.map((o) => ({
                      value: o.value,
                      label: t(`checkinGuest.sex${o.label}`),
                    }))}
                    value={form.sex}
                    onChange={(v) => updateField("sex", v ?? "")}
                    error={errors.sex}
                  />
                </SimpleGrid>
              </Stack>
            </Paper>

            {/* Official Documentation — only required for guests >= 14 */}
            {isAdult && <Divider hiddenFrom="xs" mt="xs" mb="lg" />}
            {isAdult && (
            <Paper
              p={{ base: 0, xs: "lg" }}
              radius="md"
              bg={{ base: "transparent", xs: "gray.0" }}
              bd={{ base: 0, xs: "1px solid var(--mantine-color-gray-3)" }}
            >
              <SectionHeader
                icon={<IconBadge size={20} color="var(--mantine-color-blue-7)" />}
                title={t("checkinGuest.documentation")}
              />
              <Stack gap="md">
                <SimpleGrid cols={{ base: 1, xs: 2 }} spacing="md">
                  <KeyboardSelect
                    label={t("checkinGuest.documentType")}
                    placeholder={t("checkinGuest.documentTypePlaceholder")}
                    data={DOCUMENT_TYPES.map((d) => ({
                      value: d.value,
                      label: t(`checkinGuest.docType.${d.value}`),
                    }))}
                    value={form.documentType}
                    onChange={(v) => updateField("documentType", v ?? "")}
                    error={errors.documentType}
                  />
                  <TextInput
                    label={t("checkinGuest.documentNumber")}
                    placeholder={t("checkinGuest.documentNumberPlaceholder")}
                    value={form.documentNumber}
                    onChange={(e) =>
                      updateField("documentNumber", e.currentTarget.value)
                    }
                    autoCapitalize="characters"
                    error={errors.documentNumber}
                  />
                </SimpleGrid>
                {isSpanishDoc && (
                  <TextInput
                    label={t("checkinGuest.documentSupportNumber")}
                    placeholder={t("checkinGuest.documentSupportNumberPlaceholder")}
                    description={
                      <Anchor
                        component="button"
                        type="button"
                        size="xs"
                        onClick={() => setSupportNumberHelpOpen(true)}
                      >
                        {t("checkinGuest.documentSupportNumberHint")}
                      </Anchor>
                    }
                    value={form.documentSupportNumber}
                    onChange={(e) =>
                      updateField("documentSupportNumber", e.currentTarget.value)
                    }
                    error={errors.documentSupportNumber}
                  />
                )}
              </Stack>
            </Paper>
            )}

            {/* Address — required for everyone, including minors */}
            <Divider hiddenFrom="xs" mt="xs" mb="lg" />
            <Paper
              p={{ base: 0, xs: "lg" }}
              radius="md"
              bg={{ base: "transparent", xs: "gray.0" }}
              bd={{ base: 0, xs: "1px solid var(--mantine-color-gray-3)" }}
            >
              <SectionHeader
                icon={<IconMapPin size={20} color="var(--mantine-color-blue-7)" />}
                title={t("checkinGuest.address")}
              />
              <Stack gap="md">
                <GeoLocationSelect
                  countryCode={form.residenceCountry || null}
                  provinceId={form.residenceProvinceId || null}
                  cityId={form.residenceCityId || null}
                  cityText={form.residenceCityText}
                  onChangeCountry={(code) => {
                    updateField("residenceCountry", code);
                    updateField("residenceProvinceId", "");
                    updateField("residenceCityId", "");
                    updateField("residenceCityText", "");
                  }}
                  onChangeProvince={(id) => {
                    updateField("residenceProvinceId", id ?? "");
                    updateField("residenceCityId", "");
                  }}
                  onChangeCity={(id) =>
                    updateField("residenceCityId", id ?? "")
                  }
                  onChangeCityText={(text) =>
                    updateField("residenceCityText", text)
                  }
                  errors={{
                    country: errors.residenceCountry,
                    province: errors.residenceProvinceId,
                    city: errors.residenceCityId ?? errors.residenceCityText,
                  }}
                  labels={{
                    country: t("checkinGuest.residenceCountry"),
                    countryPlaceholder: t(
                      "checkinGuest.residenceCountryPlaceholder",
                    ),
                    province: t("checkinGuest.province"),
                    provincePlaceholder: t("checkinGuest.provincePlaceholder"),
                    city: t("checkinGuest.city"),
                    cityPlaceholder: t("checkinGuest.cityPlaceholder"),
                  }}
                />
                <TextInput
                  label={t("checkinGuest.streetAddress")}
                  placeholder={t("checkinGuest.streetAddressPlaceholder")}
                  value={form.residenceAddress}
                  onChange={(e) =>
                    updateField("residenceAddress", e.currentTarget.value)
                  }
                  error={errors.residenceAddress}
                />
                <TextInput
                  label={t("checkinGuest.postalCode")}
                  placeholder={t("checkinGuest.postalCodePlaceholder")}
                  inputMode="numeric"
                  value={form.residencePostalCode}
                  onChange={(e) =>
                    updateField("residencePostalCode", e.currentTarget.value)
                  }
                  error={errors.residencePostalCode}
                />
              </Stack>
            </Paper>

            {/* Contact — required for every guest, including minors (SES rule).
                A minor without their own phone/email should use a parent's. */}
            <Divider hiddenFrom="xs" mt="xs" mb="lg" />
            <Paper
              p={{ base: 0, xs: "lg" }}
              radius="md"
              bg={{ base: "transparent", xs: "gray.0" }}
              bd={{ base: 0, xs: "1px solid var(--mantine-color-gray-3)" }}
            >
              <SectionHeader
                icon={<IconPhone size={20} color="var(--mantine-color-blue-7)" />}
                title={t("checkinGuest.contact")}
              />
              <Text size="xs" c="dimmed" mb="md">
                {t("checkinGuest.contactHint")}
              </Text>
              <Stack gap="md">
                <Box>
                  <Text size="sm" fw={500} mb={4}>
                    {t("checkinGuest.phone")}
                  </Text>
                  <Group gap="xs" align="flex-start" wrap="nowrap">
                    <KeyboardSelect
                      placeholder="+00"
                      data={phonePrefixOptions}
                      value={form.phonePrefix}
                      onChange={(v) => updateField("phonePrefix", v ?? "")}
                      error={!!errors.phone}
                      w={130}
                      styles={{ input: { minWidth: 100 } }}
                    />
                    <TextInput
                      placeholder={t("checkinGuest.phonePlaceholder")}
                      inputMode="tel"
                      value={form.phone}
                      onChange={(e) => updateField("phone", e.currentTarget.value)}
                      error={errors.phone}
                      style={{ flex: 1 }}
                    />
                  </Group>
                </Box>
                <TextInput
                  label={t("checkinGuest.email")}
                  placeholder={t("checkinGuest.emailPlaceholder")}
                  inputMode="email"
                  autoCapitalize="none"
                  value={form.email}
                  onChange={(e) => updateField("email", e.currentTarget.value)}
                  error={errors.email}
                />
              </Stack>
            </Paper>

            {/* Relationship to adult — shown when the current guest's DOB
                makes them a minor (< 18). */}
            {requiresParentesco && <Divider hiddenFrom="xs" mt="xs" mb="lg" />}
            {requiresParentesco && (
            <Paper
              p={{ base: 0, xs: "lg" }}
              radius="md"
              bg={{ base: "transparent", xs: "gray.0" }}
              bd={{ base: 0, xs: "1px solid var(--mantine-color-gray-3)" }}
            >
              <SectionHeader
                icon={<IconUser size={20} color="var(--mantine-color-blue-7)" />}
                title={t("checkinGuest.parentesco.title")}
              />
              <Text size="xs" c="dimmed" mb="md">
                {t("checkinGuest.parentesco.hint")}
              </Text>
              <KeyboardSelect
                label={t("checkinGuest.parentesco.label")}
                placeholder={t("checkinGuest.parentesco.placeholder")}
                data={PARENTESCO_CODES.map((code) => ({
                  value: code,
                  label: t(`checkinGuest.parentesco.code.${code}`),
                }))}
                value={form.relationshipCode}
                onChange={(v) => updateField("relationshipCode", v ?? "")}
                error={errors.relationshipCode}
              />
            </Paper>
            )}

            {/* Marketing opt-in — strictly optional (GDPR consent, so never
                pre-ticked, never required) and only offered to adults. */}
            {isMarketingEligible && (
              <Paper
                p="md"
                radius="md"
                bg="blue.0"
                style={{
                  border: "1px solid var(--mantine-color-blue-2)",
                }}
              >
                <Group gap={8} mb={4} wrap="nowrap">
                  <IconSparkles
                    size={18}
                    color="var(--mantine-color-blue-7)"
                    style={{ flexShrink: 0 }}
                  />
                  <Text fw={700} size="sm" c="blue.9">
                    {t("checkinGuest.marketingOptIn.title")}
                  </Text>
                </Group>
                <Text size="xs" c="dimmed" mb="sm">
                  {t("checkinGuest.marketingOptIn.perks")}
                </Text>
                <Checkbox
                  checked={form.marketingConsent}
                  onChange={(e) =>
                    updateField("marketingConsent", e.currentTarget.checked)
                  }
                  label={t("checkinGuest.marketingOptIn.label")}
                  size="sm"
                  styles={{ label: { fontSize: 13, cursor: "pointer" }, input: { cursor: "pointer" } }}
                />
                <Text size="xs" c="dimmed" mt={8} ml={30}>
                  <Trans
                    i18nKey="checkinGuest.marketingOptIn.fine"
                    components={{
                      policyLink: (
                        // eslint-disable-next-line jsx-a11y/anchor-has-content -- content injected by Trans
                        <a
                          href={`${getLegalUrl()}/privacy-policy`}
                          target="_blank"
                          rel="noopener noreferrer"
                        />
                      ),
                    }}
                  />
                </Text>
              </Paper>
            )}

            {/* Actions */}
            <Stack gap="sm" mt="sm">
              <Button
                fullWidth
                size="lg"
                color="dark"
                rightSection={<IconSignature size={20} />}
                onClick={handleSubmit}
                loading={submitting}
                disabled={submitting}
                style={{
                  fontWeight: 700,
                  fontSize: "1.1rem",
                  height: 56,
                }}
              >
                {submitting
                  ? t("checkinGuest.submitting")
                  : t("checkinGuest.signAndSubmit")}
              </Button>
              {/* GDPR Art. 13 first-layer notice. Processing is based on a
                  legal obligation (RD 933/2021), so this informs — it must
                  not ask for consent or agreement. */}
              <Text size="xs" c="dimmed" lh={1.5} mt="lg">
                <Trans
                  i18nKey="checkinGuest.privacyNotice"
                  components={{
                    bold: <Text span size="xs" fw={600} />,
                    policyLink: (
                      // eslint-disable-next-line jsx-a11y/anchor-has-content -- content injected by Trans
                      <a
                        href={`${getLegalUrl()}/privacy-policy`}
                        target="_blank"
                        rel="noopener noreferrer"
                      />
                    ),
                  }}
                />
              </Text>
            </Stack>

          </Stack>
        </Box>

      {/* Validation errors modal */}
      {/* Support number help modal */}
      <Modal
        opened={supportNumberHelpOpen}
        onClose={() => setSupportNumberHelpOpen(false)}
        centered
        radius="lg"
        size="auto"
        title={t("checkinGuest.documentSupportNumber")}
        overlayProps={{ backgroundOpacity: 0.55, blur: 3 }}
        styles={{
          title: {
            color: "var(--mantine-color-blue-7)",
            fontSize: "var(--mantine-font-size-sm)",
            fontWeight: 600,
          },
        }}
      >
        <Image
          src="/imgs/dni-nie-soporte.jpg"
          alt={t("checkinGuest.documentSupportNumberHelpAlt")}
          radius="md"
          w="auto"
          maw="100%"
          mah="65vh"
        />
      </Modal>

      <Modal
        opened={validationErrorsOpen}
        onClose={() => setValidationErrorsOpen(false)}
        centered
        withCloseButton={false}
        radius="lg"
        size="sm"
        padding="xl"
        overlayProps={{ backgroundOpacity: 0.55, blur: 3 }}
      >
        <Stack align="center" gap="md" py="sm">
          <Box
            style={{
              width: 80,
              height: 80,
              borderRadius: "50%",
              backgroundColor: "var(--mantine-color-red-0)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <IconAlertCircle
              size={52}
              color="var(--mantine-color-red-6)"
            />
          </Box>
          <Title
            order={3}
            ta="center"
            style={{ letterSpacing: "-0.02em" }}
          >
            {t("checkinGuest.validationErrorsTitle")}
          </Title>
          <Text ta="center" c="dimmed" size="sm">
            {t("checkinGuest.validationErrorsBody")}
          </Text>
          <Button
            fullWidth
            color="dark"
            size="md"
            onClick={() => setValidationErrorsOpen(false)}
            style={{ fontWeight: 600 }}
          >
            {t("checkinGuest.validationErrorsClose")}
          </Button>
        </Stack>
      </Modal>

      {/* Submit error modal */}
      <Modal
        opened={!!submitError}
        onClose={() => setSubmitError(null)}
        centered
        withCloseButton={false}
        radius="lg"
        size="sm"
        padding="xl"
        overlayProps={{ backgroundOpacity: 0.55, blur: 3 }}
      >
        <Stack align="center" gap="md" py="sm">
          <Box
            style={{
              width: 80,
              height: 80,
              borderRadius: "50%",
              backgroundColor: "var(--mantine-color-red-0)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <IconAlertCircle
              size={52}
              color="var(--mantine-color-red-6)"
            />
          </Box>
          <Title
            order={3}
            ta="center"
            style={{ letterSpacing: "-0.02em" }}
          >
            {t("checkinGuest.submitErrorTitle")}
          </Title>
          <Text ta="center" c="dimmed" size="sm">
            {submitError}
          </Text>
          <Button
            fullWidth
            color="dark"
            size="md"
            onClick={() => setSubmitError(null)}
            style={{ fontWeight: 600 }}
          >
            {t("checkinGuest.validationErrorsClose")}
          </Button>
        </Stack>
      </Modal>

      {/* Success modal — shown after the server confirms the registration */}
      <Modal
        opened={successOpen}
        onClose={() => setSuccessOpen(false)}
        centered
        withCloseButton={false}
        radius="lg"
        size="sm"
        padding="xl"
        overlayProps={{ backgroundOpacity: 0.55, blur: 3 }}
      >
        <Stack align="center" gap="md" py="sm">
          {successContext?.checkinCompleted ? (
            <>
              <Box
                style={{
                  width: 80,
                  height: 80,
                  borderRadius: "50%",
                  backgroundColor: "var(--mantine-color-green-0)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <IconRosetteDiscountCheckFilled
                  size={52}
                  color="var(--mantine-color-green-6)"
                />
              </Box>
              <Title
                order={3}
                ta="center"
                style={{ letterSpacing: "-0.02em" }}
              >
                {t("checkinGuest.completedTitle")}
              </Title>
              <Text ta="center" c="dimmed" size="sm">
                {successContext?.firstName
                  ? t("checkinGuest.completedBodyNamed", {
                      name: successContext.firstName,
                    })
                  : t("checkinGuest.completedBodyGeneric")}
              </Text>
              <Paper
                p="sm"
                radius="md"
                bg="gray.0"
                withBorder
                w="100%"
                style={{ borderColor: "var(--mantine-color-gray-3)" }}
              >
                <Group gap="sm" align="flex-start" wrap="nowrap">
                  <IconGavel
                    size={18}
                    color="var(--mantine-color-orange-7)"
                    style={{ flexShrink: 0, marginTop: 2 }}
                  />
                  <Text size="xs" c="dimmed" lh={1.5}>
                    {t("checkinGuest.complianceConfirmation")}
                  </Text>
                </Group>
              </Paper>
              <Stack gap="sm" w="100%" mt="xs">
                <Button
                  fullWidth
                  size="md"
                  color="dark"
                  leftSection={<IconHome size={18} />}
                  onClick={handleBackToCheckin}
                  style={{
                    fontWeight: 700,
                  }}
                >
                  {t("checkinGuest.backToCheckin")}
                </Button>
                <Button
                  fullWidth
                  size="md"
                  variant="subtle"
                  color="blue"
                  leftSection={<IconLink size={18} />}
                  onClick={handleCopyFormLink}
                >
                  {t("checkinGuest.copyFormLink")}
                </Button>
              </Stack>
            </>
          ) : (
            <>
              <ThemeIcon
                size={80}
                radius="xl"
                variant="light"
                color="green"
              >
                <IconCircleCheckFilled size={56} />
              </ThemeIcon>
              <Title
                order={3}
                ta="center"
                style={{ letterSpacing: "-0.02em" }}
              >
                {t("checkinGuest.successTitle")}
              </Title>
              <Text ta="center" c="dimmed" size="sm">
                {successContext?.firstName
                  ? t("checkinGuest.successBodyNamed", {
                      name: successContext.firstName,
                    })
                  : t("checkinGuest.successBodyGeneric")}
              </Text>
              <Stack gap="sm" w="100%" mt="md">
                <Button
                  fullWidth
                  size="md"
                  color="dark"
                  leftSection={<IconUserPlus size={18} />}
                  onClick={handleRegisterNextGuest}
                  style={{
                    fontWeight: 700,
                  }}
                >
                  {t("checkinGuest.registerNextGuest")}
                </Button>
                <Button
                  fullWidth
                  size="md"
                  variant="light"
                  color="gray"
                  leftSection={<IconHome size={18} />}
                  onClick={handleBackToCheckin}
                  style={{
                    fontWeight: 600,
                  }}
                >
                  {t("checkinGuest.backToCheckin")}
                </Button>
                <Button
                  fullWidth
                  size="md"
                  variant="subtle"
                  color="blue"
                  leftSection={<IconLink size={18} />}
                  onClick={handleCopyFormLink}
                >
                  {t("checkinGuest.copyFormLink")}
                </Button>
              </Stack>
            </>
          )}
        </Stack>
      </Modal>
    </>
  );
}
