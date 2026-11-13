import {
  Box,
  CloseButton,
  Container, Divider,
  Group,
  Input,
  NumberInput,
  SegmentedControl,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { useForm } from "@mantine/form";
import { useNavigate } from "react-router";
import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import type { CreatePropertyRequest } from "~/types/properties";
import { SPAIN_ALPHA3 } from "~/types/geo";
import { IconCheck } from "@tabler/icons-react";
import { useState, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { showSuccessToast } from "~/lib/toast";
import { ContentHeader } from "~/components/content-header";
import { MainButton } from "~/components/main-button/main-button";
import { SecondaryButton } from "~/components/secondary-button/secondary-button";
import { ErrorMessage } from "~/components/error-message";
import { GeoLocationSelect } from "~/components/geo-location-select";
import type { Route } from "./+types/properties-new";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Kasify | Add Property" }];
}

export default function NewProperty() {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const apiClient = useMemo(
    () => ApiClient.createFromBrowser(createApiConfig()),
    [],
  );

  const form = useForm({
    validateInputOnBlur: true,
    validateInputOnChange: ["zipCode", "airbnbIcalUrl", "bookingIcalUrl"],
    initialValues: {
      name: "",
      address: "",
      countryCode: SPAIN_ALPHA3,
      provinceId: "",
      cityId: "",
      cityText: "",
      zipCode: "",
      roomsCount: 1,
      hasInternet: true,
      airbnbIcalUrl: "",
      bookingIcalUrl: "",
    },
    validate: {
      name: (value) =>
        value.trim() ? null : t("propertiesNew.validateName"),
      address: (value) =>
        value.trim() ? null : t("propertiesNew.validateAddress"),
      provinceId: (value, values) =>
        values.countryCode === SPAIN_ALPHA3 && !value
          ? t("propertiesNew.validateProvince")
          : null,
      cityId: (value, values) =>
        values.countryCode === SPAIN_ALPHA3 && !value
          ? t("propertiesNew.validateCity")
          : null,
      cityText: (value, values) =>
        values.countryCode !== SPAIN_ALPHA3 && !value.trim()
          ? t("propertiesNew.validateCity")
          : null,
      zipCode: (value) => {
        if (!value.trim()) return t("propertiesNew.validateZipCodeRequired");
        return /^\d+$/.test(value)
          ? null
          : t("propertiesNew.validatePostalCode");
      },
      roomsCount: (value) =>
        Number.isInteger(value) && value >= 1
          ? null
          : t("propertiesNew.validateRooms"),
      airbnbIcalUrl: (value) => {
        if (!value) return null;
        try {
          new URL(value);
          return null;
        } catch {
          return t("propertiesNew.validateUrl");
        }
      },
      bookingIcalUrl: (value) => {
        if (!value) return null;
        try {
          new URL(value);
          return null;
        } catch {
          return t("propertiesNew.validateUrl");
        }
      },
    },
  });

  const isSpain = form.values.countryCode === SPAIN_ALPHA3;

  const FIELD_IDS = {
    name: "property-new-name",
    countryCode: "property-new-country",
    provinceId: "property-new-province",
    cityId: "property-new-city",
    cityText: "property-new-city",
    zipCode: "property-new-zip",
    address: "property-new-address",
    roomsCount: "property-new-rooms",
    airbnbIcalUrl: "property-new-airbnb",
    bookingIcalUrl: "property-new-booking",
  } as const;

  // Visual order of fields in the rendered form. The first one with an error wins.
  const FIELD_ORDER: (keyof typeof FIELD_IDS)[] = [
    "name",
    "countryCode",
    "provinceId",
    "cityId",
    "cityText",
    "zipCode",
    "address",
    "roomsCount",
    "airbnbIcalUrl",
    "bookingIcalUrl",
  ];

  const handleSubmitError = (errs: Record<string, unknown>) => {
    const first = FIELD_ORDER.find((f) => Boolean(errs[f]));
    if (!first) return;
    const el = document.getElementById(FIELD_IDS[first]);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    // Focus on the next paint so the smooth scroll has a chance to start.
    requestAnimationFrame(() => el.focus({ preventScroll: true }));
  };

  const handleSubmit = async (values: typeof form.values) => {
    setLoading(true);
    setError(null);

    try {
      const propertyData: CreatePropertyRequest = {
        name: values.name,
        address: values.address,
        zip_code: values.zipCode,
        country_code: values.countryCode,
        rooms_count: values.roomsCount,
        has_internet: values.hasInternet,
      };

      if (isSpain) {
        propertyData.province_id = values.provinceId;
        propertyData.city_id = values.cityId;
      } else {
        propertyData.city = values.cityText;
      }

      if (values.airbnbIcalUrl) {
        propertyData.airbnb_ical_url = values.airbnbIcalUrl;
      }

      if (values.bookingIcalUrl) {
        propertyData.booking_ical_url = values.bookingIcalUrl;
      }

      await apiClient.createProperty(propertyData);
      showSuccessToast(t("propertiesNew.successToast"));
      navigate("/properties");
    } catch (err) {
      const apiError = handleApiError(err);
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        title={t("propertiesNew.title")}
        subtitle={t("propertiesNew.subtitle")}
      />

      {error && <ErrorMessage message={error} />}

      <Box
        component="form"
        noValidate
        onSubmit={form.onSubmit(handleSubmit, handleSubmitError)}
        style={{
          backgroundColor: "white",
          borderRadius: 16,
          padding: 32,
          maxWidth: 600,
        }}
      >
        <Stack gap="lg">
          <Text fw={600} size="lg">
            {t("propertiesNew.sectionDetails")}
          </Text>

          <TextInput
            id={FIELD_IDS.name}
            label={t("propertiesNew.fieldName")}
            placeholder={t("propertiesNew.fieldNamePlaceholder")}
            required
            disabled={loading}
            {...form.getInputProps("name")}
          />

          <Divider mt="12" mb="4" />

          <GeoLocationSelect
            countryCode={form.values.countryCode}
            provinceId={form.values.provinceId || null}
            cityId={form.values.cityId || null}
            cityText={form.values.cityText}
            onChangeCountry={(code) => {
              form.setFieldValue("countryCode", code);
              form.setFieldValue("provinceId", "");
              form.setFieldValue("cityId", "");
              form.setFieldValue("cityText", "");
            }}
            onChangeProvince={(id) => {
              form.setFieldValue("provinceId", id ?? "");
              form.setFieldValue("cityId", "");
            }}
            onChangeCity={(id) => form.setFieldValue("cityId", id ?? "")}
            onChangeCityText={(text) => form.setFieldValue("cityText", text)}
            disableCountry
            required
            ids={{
              country: FIELD_IDS.countryCode,
              province: FIELD_IDS.provinceId,
              city: FIELD_IDS.cityId,
            }}
            errors={{
              province: form.errors.provinceId as string | undefined,
              city:
                (form.errors.cityId as string | undefined) ??
                (form.errors.cityText as string | undefined),
            }}
            labels={{
              country: t("propertiesNew.fieldCountry"),
              countryPlaceholder: t("propertiesNew.fieldCountryPlaceholder"),
              province: t("propertiesNew.fieldProvince"),
              provincePlaceholder: t("propertiesNew.fieldProvincePlaceholder"),
              city: t("propertiesNew.fieldCity"),
              cityPlaceholder: t("propertiesNew.fieldCityPlaceholder"),
            }}
          />

          <TextInput
            id={FIELD_IDS.zipCode}
            label={t("propertiesNew.fieldPostalCode")}
            placeholder={t("propertiesNew.fieldPostalCodePlaceholder")}
            required
            disabled={loading}
            {...form.getInputProps("zipCode")}
          />

          <TextInput
              id={FIELD_IDS.address}
              label={t("propertiesNew.fieldAddress")}
              placeholder={t("propertiesNew.fieldAddressPlaceholder")}
              required
              disabled={loading}
              {...form.getInputProps("address")}
          />

          <Group grow align="flex-start">
            <NumberInput
              id={FIELD_IDS.roomsCount}
              label={t("propertiesNew.fieldRooms")}
              min={1}
              allowDecimal={false}
              disabled={loading}
              {...form.getInputProps("roomsCount")}
            />

            <Input.Wrapper
              label={t("propertiesNew.fieldHasInternet")}
              styles={{
                label: {
                  fontSize: "0.75rem",
                  fontWeight: 500,
                  color: "#727272",
                  margin: 0,
                  paddingBottom: 0,
                },
              }}
            >
              <SegmentedControl
                fullWidth
                disabled={loading}
                styles={{
                  root: { height: 46 }, // match the 44px input height from the theme in root.tsx
                  label: {
                    height: "100%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  },
                }}
                value={form.values.hasInternet ? "yes" : "no"}
                onChange={(value) =>
                  form.setFieldValue("hasInternet", value === "yes")
                }
                data={[
                  { value: "yes", label: t("propertiesNew.hasInternetYes") },
                  { value: "no", label: t("propertiesNew.hasInternetNo") },
                ]}
              />
            </Input.Wrapper>
          </Group>

          <Box mt="md">
            <Text fw={600} size="lg" mb="md">
              {t("propertiesNew.sectionAirbnb")}
            </Text>

            <TextInput
              id={FIELD_IDS.airbnbIcalUrl}
              label={t("propertiesNew.fieldAirbnbLabel")}
              description={t("propertiesNew.fieldAirbnbDescription")}
              placeholder={t("propertiesNew.fieldAirbnbPlaceholder")}
              disabled={loading}
              {...form.getInputProps("airbnbIcalUrl")}
              styles={{ input: { paddingRight: 36 } }}
              rightSectionPointerEvents="auto"
              rightSection={
                form.values.airbnbIcalUrl && (
                  <CloseButton
                    size="sm"
                    onClick={() => form.setFieldValue("airbnbIcalUrl", "")}
                    aria-label={t("propertiesNew.clearUrl")}
                  />
                )
              }
            />
            <Text size="xs" c="dimmed" mt="xs">
              {t("propertiesNew.fieldAirbnbSyncNotice")}
            </Text>
          </Box>

          <Box mt="md">
            <Text fw={600} size="lg" mb="md">
              {t("propertiesNew.sectionBooking")}
            </Text>

            <TextInput
              id={FIELD_IDS.bookingIcalUrl}
              label={t("propertiesNew.fieldBookingLabel")}
              description={t("propertiesNew.fieldBookingDescription")}
              placeholder={t("propertiesNew.fieldBookingPlaceholder")}
              disabled={loading}
              {...form.getInputProps("bookingIcalUrl")}
              styles={{ input: { paddingRight: 36 } }}
              rightSectionPointerEvents="auto"
              rightSection={
                form.values.bookingIcalUrl && (
                  <CloseButton
                    size="sm"
                    onClick={() => form.setFieldValue("bookingIcalUrl", "")}
                    aria-label={t("propertiesNew.clearUrl")}
                  />
                )
              }
            />
            <Text size="xs" c="dimmed" mt="xs">
              {t("propertiesNew.fieldBookingSyncNotice")}
            </Text>
          </Box>

          <Group justify="flex-end" mt="xl">
            <SecondaryButton onClick={() => navigate("/properties")}>
              {t("propertiesNew.cancel")}
            </SecondaryButton>
            <MainButton
              type="submit"
              loading={loading}
              leftSection={<IconCheck size={16} />}
            >
              {loading ? t("propertiesNew.creating") : t("propertiesNew.create")}
            </MainButton>
          </Group>
        </Stack>
      </Box>
    </Container>
  );
}
