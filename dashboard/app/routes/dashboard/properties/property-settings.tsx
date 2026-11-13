import { ApiClient, createApiConfig, handleApiError } from "~/lib/api-client";
import { useLoaderData, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import {
  ActionIcon,
  Box,
  CloseButton,
  Container,
  FileButton,
  Group,
  Image,
  Input,
  Loader,
  NumberInput,
  Overlay,
  SegmentedControl,
  Stack,
  Text,
  TextInput,
} from "@mantine/core";
import { TimePicker } from "@mantine/dates";
import { useForm } from "@mantine/form";
import { IconCheck, IconPhoto, IconTrash, IconUpload } from "@tabler/icons-react";
import type { Route } from "./+types/property-settings";
import { ContentHeader } from "~/components/content-header";
import { ErrorMessage } from "~/components/error-message";
import { useRef, useState } from "react";
import { MainButton } from "~/components/main-button/main-button";
import { SecondaryButton } from "~/components/secondary-button/secondary-button";
import { showErrorToast, showSuccessToast } from "~/lib/toast";
import { isValidSpanishTaxId } from "~/lib/spanish-tax-id";
import { CleanerSelect } from "~/components/cleaner-select";
import { GeoLocationSelect } from "~/components/geo-location-select";
import { SPAIN_ALPHA3 } from "~/types/geo";

function toTimeInputValue(value: string | undefined | null): string {
  if (!value) return "";
  const [h = "", m = ""] = value.split(":");
  if (!h || !m) return "";
  return `${h.padStart(2, "0")}:${m.padStart(2, "0")}`;
}

function fromTimeInputValue(value: string): string | null {
  if (!value) return null;
  const [h, m] = value.split(":");
  if (!h || !m) return null;
  return `${h.padStart(2, "0")}:${m.padStart(2, "0")}`;
}

export async function loader({ request, params }: Route.LoaderArgs) {
  const propertyId = params.id;

  if (!propertyId) {
    throw new Response("Property ID is required", { status: 400 });
  }

  try {
    const apiClient = await ApiClient.createAuthenticated(
      request,
      createApiConfig(),
    );
    const response = await apiClient.getProperty(propertyId);

    return {
      property: response.data,
      error: null,
    };
  } catch (error) {
    const apiError = handleApiError(error);
    return { property: null, error: apiError.message };
  }
}

export function meta({ data }: Route.MetaArgs) {
  const propertyName = data?.property?.name ?? "Property";
  return [{ title: `Kasify | ${propertyName} Settings` }];
}

export default function PropertySettings() {
  const { property, error: loadError } = useLoaderData<typeof loader>();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const airbnbCalendar = property?.calendars?.find(
    (c) => c.channel === "Airbnb",
  );
  const bookingCalendar = property?.calendars?.find(
    (c) => c.channel === "Booking",
  );

  const form = useForm({
    validateInputOnBlur: true,
    validateInputOnChange: ["zipCode", "airbnbIcalUrl", "bookingIcalUrl"],
    initialValues: {
      name: property?.name ?? "",
      address: property?.address ?? "",
      cityText: property?.city ?? "",
      zipCode: property?.zip_code ?? "",
      arrendadorNif: property?.arrendador_nif ?? "",
      roomsCount: property?.rooms_count ?? 1,
      hasInternet: property?.has_internet ?? true,
      countryCode: property?.country_code ?? SPAIN_ALPHA3,
      provinceId: property?.province_id ?? "",
      cityId: property?.city_id ?? "",
      airbnbIcalUrl: airbnbCalendar?.url ?? "",
      bookingIcalUrl: bookingCalendar?.url ?? "",
      defaultCleanerId: property?.default_cleaner?.id ?? "",
      checkinTime: toTimeInputValue(property?.checkin_time),
      checkoutTime: toTimeInputValue(property?.checkout_time),
    },
    validate: {
      name: (value) =>
        value.trim() ? null : t("propertySettings.validateName"),
      provinceId: (value, values) =>
        values.countryCode === SPAIN_ALPHA3 && !value
          ? t("propertySettings.validateProvince")
          : null,
      cityId: (value, values) =>
        values.countryCode === SPAIN_ALPHA3 && !value
          ? t("propertySettings.validateCity")
          : null,
      cityText: (value, values) =>
        values.countryCode !== SPAIN_ALPHA3 && !value.trim()
          ? t("propertySettings.validateCity")
          : null,
      zipCode: (value) => {
        if (!value.trim()) return null;
        return /^\d+$/.test(value)
          ? null
          : t("propertySettings.validatePostalCode");
      },
      arrendadorNif: (value) => {
        if (!value.trim()) return null;
        return isValidSpanishTaxId(value)
          ? null
          : t("propertySettings.validateArrendadorNif");
      },
      roomsCount: (value) =>
        Number.isInteger(value) && value >= 1
          ? null
          : t("propertySettings.validateRooms"),
      airbnbIcalUrl: (value) => {
        if (!value) return null;
        try {
          new URL(value);
          return null;
        } catch {
          return t("propertySettings.validateUrl");
        }
      },
      bookingIcalUrl: (value) => {
        if (!value) return null;
        try {
          new URL(value);
          return null;
        } catch {
          return t("propertySettings.validateUrl");
        }
      },
    },
  });

  const isSpain = form.values.countryCode === SPAIN_ALPHA3;

  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [coverImageUrl, setCoverImageUrl] = useState<string | null>(
    property?.cover_image_url ?? null,
  );
  const [isUploadingCover, setIsUploadingCover] = useState(false);
  const [isDeletingCover, setIsDeletingCover] = useState(false);
  const resetFileRef = useRef<() => void>(null);

  const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
  const MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5 MB

  const handleCoverUpload = async (file: File | null) => {
    if (!file || !property) return;

    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      showErrorToast(t("propertySettings.coverImageInvalidType"));
      return;
    }
    if (file.size > MAX_IMAGE_SIZE) {
      showErrorToast(t("propertySettings.coverImageTooLarge"));
      return;
    }

    setIsUploadingCover(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      const response = await apiClient.uploadPropertyCoverImage(
        property.id,
        file,
      );
      setCoverImageUrl(response.data.url);
      showSuccessToast(t("propertySettings.coverImageSuccess"), "✓");
    } catch {
      showErrorToast(t("propertySettings.coverImageError"));
    } finally {
      setIsUploadingCover(false);
      resetFileRef.current?.();
    }
  };

  const handleCoverRemove = async () => {
    if (!property) return;

    setIsDeletingCover(true);
    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      await apiClient.deletePropertyCoverImage(property.id);
      setCoverImageUrl(null);
      showSuccessToast(t("propertySettings.coverImageRemoved"), "✓");
    } catch {
      showErrorToast(t("propertySettings.coverImageRemoveError"));
    } finally {
      setIsDeletingCover(false);
    }
  };

  if (loadError || !property) {
    return (
      <Box>
        <ErrorMessage
          title={t("propertySettings.error")}
          message={loadError ?? t("propertySettings.notFound")}
        />
      </Box>
    );
  }

  const breadcrumbItems = [
    { title: t("properties.title"), href: "/properties" },
    { title: property.name, href: `/properties/${property.id}` },
    { title: t("propertySettings.breadcrumbSettings"), href: "#" },
  ];

  const handleSave = async (values: typeof form.values) => {
    setIsSaving(true);
    setError(null);

    try {
      const apiClient = ApiClient.createFromBrowser(createApiConfig());
      const payload: Parameters<typeof apiClient.updateProperty>[1] = {
        name: values.name,
        address: values.address,
        zip_code: values.zipCode,
        // Always sent: the backend treats "" as "clear the NIF" (omitting it would keep the old one).
        arrendador_nif: values.arrendadorNif.trim().toUpperCase(),
        country_code: values.countryCode,
        rooms_count: values.roomsCount,
        has_internet: values.hasInternet,
        airbnb_ical_url: values.airbnbIcalUrl || undefined,
        booking_ical_url: values.bookingIcalUrl || undefined,
        default_cleaner_id: values.defaultCleanerId || undefined,
        checkin_time: fromTimeInputValue(values.checkinTime) ?? undefined,
        checkout_time: fromTimeInputValue(values.checkoutTime) ?? undefined,
      };
      if (values.countryCode === SPAIN_ALPHA3) {
        payload.province_id = values.provinceId;
        payload.city_id = values.cityId;
      } else {
        payload.city = values.cityText;
      }
      await apiClient.updateProperty(property.id, payload);
      showSuccessToast(t("propertySettings.saveSuccess"), "✓");
      navigate(`/properties/${property.id}`);
    } catch (err: any) {
      console.error("Error saving property:", err);
      setError(t("propertySettings.saveError"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    navigate(`/properties/${property.id}`);
  };

  return (
    <Container fluid={true}>
      <ContentHeader
        breadcrumbs={breadcrumbItems}
        subtitle={t("propertySettings.subtitle")}
      />

      {error && <ErrorMessage message={error} />}

      <Box
        component="form"
        onSubmit={form.onSubmit(handleSave)}
        style={{
          backgroundColor: "white",
          borderRadius: 16,
          padding: 32,
          maxWidth: 600,
        }}
      >
        <Stack gap="lg">
          <Box>
            <Text fw={600} size="lg" mb={4}>
              {t("propertySettings.sectionCoverImage")}
            </Text>
            <Text size="sm" c="dimmed" mb="md">
              {t("propertySettings.coverImageDescription")}
            </Text>

            {coverImageUrl ? (
              <Box
                pos="relative"
                style={{ borderRadius: 12, overflow: "hidden", maxWidth: 400 }}
              >
                <Image
                  src={coverImageUrl}
                  alt={property.name}
                  h={200}
                  fit="cover"
                  radius="md"
                />
                <Group
                  pos="absolute"
                  bottom={8}
                  right={8}
                  gap={4}
                >
                  <FileButton
                    resetRef={resetFileRef}
                    onChange={handleCoverUpload}
                    accept={ACCEPTED_IMAGE_TYPES.join(",")}
                  >
                    {(props) => (
                      <ActionIcon
                        {...props}
                        variant="filled"
                        color="dark"
                        size="lg"
                        radius="xl"
                        loading={isUploadingCover}
                        disabled={isDeletingCover}
                      >
                        <IconUpload size={16} />
                      </ActionIcon>
                    )}
                  </FileButton>
                  <ActionIcon
                    variant="filled"
                    color="red"
                    size="lg"
                    radius="xl"
                    loading={isDeletingCover}
                    disabled={isUploadingCover}
                    onClick={handleCoverRemove}
                  >
                    <IconTrash size={16} />
                  </ActionIcon>
                </Group>
                {isUploadingCover && (
                  <Overlay color="#000" backgroundOpacity={0.4} radius="md">
                    <Group justify="center" align="center" h="100%">
                      <Loader color="white" size="sm" />
                      <Text c="white" size="sm" fw={500}>
                        {t("propertySettings.coverImageUploading")}
                      </Text>
                    </Group>
                  </Overlay>
                )}
              </Box>
            ) : (
              <FileButton
                resetRef={resetFileRef}
                onChange={handleCoverUpload}
                accept={ACCEPTED_IMAGE_TYPES.join(",")}
              >
                {(props) => (
                  <Box
                    {...props}
                    component="button"
                    type="button"
                    style={{
                      border: "2px dashed var(--mantine-color-gray-3)",
                      borderRadius: 12,
                      padding: 32,
                      maxWidth: 400,
                      cursor: "pointer",
                      background: "transparent",
                      width: "100%",
                    }}
                  >
                    <Stack align="center" gap="xs">
                      {isUploadingCover ? (
                        <Loader size="sm" />
                      ) : (
                        <IconPhoto
                          size={32}
                          color="var(--mantine-color-gray-5)"
                        />
                      )}
                      <Text size="sm" fw={500} c="dimmed">
                        {isUploadingCover
                          ? t("propertySettings.coverImageUploading")
                          : t("propertySettings.coverImageUpload")}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {t("propertySettings.coverImageHint")}
                      </Text>
                    </Stack>
                  </Box>
                )}
              </FileButton>
            )}
          </Box>

          <Text fw={600} size="lg">
            {t("propertySettings.sectionDetails")}
          </Text>

          <TextInput
            label={t("propertySettings.fieldName")}
            placeholder={t("propertySettings.fieldNamePlaceholder")}
            required
            {...form.getInputProps("name")}
          />

          <TextInput
            label={t("propertySettings.fieldAddress")}
            placeholder={t("propertySettings.fieldAddressPlaceholder")}
            {...form.getInputProps("address")}
          />

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
            errors={{
              province: form.errors.provinceId as string | undefined,
              city:
                (form.errors.cityId as string | undefined) ??
                (form.errors.cityText as string | undefined),
            }}
            labels={{
              country: t("propertySettings.fieldCountry"),
              countryPlaceholder: t("propertySettings.fieldCountryPlaceholder"),
              province: t("propertySettings.fieldProvince"),
              provincePlaceholder: t("propertySettings.fieldProvincePlaceholder"),
              city: t("propertySettings.fieldCity"),
              cityPlaceholder: t("propertySettings.fieldCityPlaceholder"),
            }}
          />

          <TextInput
            label={t("propertySettings.fieldPostalCode")}
            placeholder={t("propertySettings.fieldPostalCodePlaceholder")}
            {...form.getInputProps("zipCode")}
          />

          <TextInput
            label={t("propertySettings.fieldArrendadorNif")}
            description={t("propertySettings.fieldArrendadorNifDescription")}
            placeholder={t("propertySettings.fieldArrendadorNifPlaceholder")}
            maxLength={12}
            {...form.getInputProps("arrendadorNif")}
          />

          <Group grow align="flex-start">
            <NumberInput
              label={t("propertySettings.fieldRooms")}
              min={1}
              allowDecimal={false}
              {...form.getInputProps("roomsCount")}
            />

            <Input.Wrapper
              label={t("propertySettings.fieldHasInternet")}
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
                  { value: "yes", label: t("propertySettings.hasInternetYes") },
                  { value: "no", label: t("propertySettings.hasInternetNo") },
                ]}
              />
            </Input.Wrapper>
          </Group>

          <Box mt="md">
            <Text fw={600} size="lg" mb="md">
              {t("propertySettings.sectionAirbnb")}
            </Text>

            <TextInput
              label={t("propertySettings.fieldAirbnbLabel")}
              description={t("propertySettings.fieldAirbnbDescription")}
              placeholder={t("propertySettings.fieldAirbnbPlaceholder")}
              {...form.getInputProps("airbnbIcalUrl")}
              styles={{ input: { paddingRight: 36 } }}
              rightSectionPointerEvents="auto"
              rightSection={
                form.values.airbnbIcalUrl && (
                  <CloseButton
                    size="sm"
                    onClick={() => form.setFieldValue("airbnbIcalUrl", "")}
                    aria-label={t("propertySettings.clearUrl")}
                  />
                )
              }
            />
            <Text size="xs" c="dimmed" mt="xs">
              {t("propertySettings.fieldAirbnbSyncNotice")}
            </Text>
          </Box>

          <Box mt="md">
            <Text fw={600} size="lg" mb="md">
              {t("propertySettings.sectionBooking")}
            </Text>

            <TextInput
              label={t("propertySettings.fieldBookingLabel")}
              description={t("propertySettings.fieldBookingDescription")}
              placeholder={t("propertySettings.fieldBookingPlaceholder")}
              {...form.getInputProps("bookingIcalUrl")}
              styles={{ input: { paddingRight: 36 } }}
              rightSectionPointerEvents="auto"
              rightSection={
                form.values.bookingIcalUrl && (
                  <CloseButton
                    size="sm"
                    onClick={() => form.setFieldValue("bookingIcalUrl", "")}
                    aria-label={t("propertySettings.clearUrl")}
                  />
                )
              }
            />
            <Text size="xs" c="dimmed" mt="xs">
              {t("propertySettings.fieldBookingSyncNotice")}
            </Text>
          </Box>

          <Box mt="md">
            <Text fw={600} size="lg" mb={4}>
              {t("propertySettings.sectionStayTimes")}
            </Text>
            <Text size="sm" c="dimmed" mb="md">
              {t("propertySettings.stayTimesDescription")}
            </Text>

            <Group grow align="flex-start">
              <TimePicker
                label={t("propertySettings.fieldCheckinTime")}
                value={form.values.checkinTime}
                onChange={(value) => form.setFieldValue("checkinTime", value)}
              />
              <TimePicker
                label={t("propertySettings.fieldCheckoutTime")}
                value={form.values.checkoutTime}
                onChange={(value) => form.setFieldValue("checkoutTime", value)}
              />
            </Group>
          </Box>

          <Box mt="md">
            <Text fw={600} size="lg" mb="md">
              {t("propertySettings.sectionCleaning")}
            </Text>

            <CleanerSelect
              team={property.team ?? []}
              label={t("propertySettings.defaultCleaner")}
              description={t("propertySettings.defaultCleanerDescription")}
              value={form.values.defaultCleanerId || null}
              onChange={(value) =>
                form.setFieldValue("defaultCleanerId", value ?? "")
              }
            />
          </Box>

          <Group justify="flex-end" mt="xl">
            <SecondaryButton onClick={handleCancel}>{t("propertySettings.cancel")}</SecondaryButton>
            <MainButton
              type="submit"
              loading={isSaving}
              disabled={!form.isValid()}
              leftSection={<IconCheck size={16} />}
            >
              {t("propertySettings.saveChanges")}
            </MainButton>
          </Group>
        </Stack>
      </Box>
    </Container>
  );
}
