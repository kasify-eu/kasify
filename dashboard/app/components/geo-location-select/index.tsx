import { Select, SimpleGrid, TextInput } from "@mantine/core";
import type { OptionsFilter } from "@mantine/core";
import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiClient, createApiConfig } from "~/lib/api-client";
import { KeyboardSelect } from "~/components/keyboard-select";
import {
  SPAIN_ALPHA3,
  type GeoCity,
  type GeoCountry,
  type GeoProvince,
} from "~/types/geo";

const normalizeSearch = (s: string) =>
  s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

const unaccentFilter: OptionsFilter = ({ options, search }) => {
  const q = normalizeSearch(search);
  return options.filter((o) =>
    "label" in o && typeof o.label === "string"
      ? normalizeSearch(o.label).includes(q)
      : false,
  );
};

interface GeoLocationSelectLabels {
  country: string;
  countryPlaceholder: string;
  province: string;
  provincePlaceholder: string;
  city: string;
  cityPlaceholder: string;
}

export interface GeoLocationSelectProps {
  /** ISO 3166-1 alpha-3 country code (e.g. "ESP"). */
  countryCode: string | null;
  /** Province UUID (only meaningful when countryCode === "ESP"). */
  provinceId: string | null;
  /** City UUID (only meaningful when countryCode === "ESP"). */
  cityId: string | null;
  /** Free-text city; used when countryCode is not Spain, and as the fallback display when an
   *  edit form preselects a `cityId` that is not yet present in the loaded cities list. */
  cityText: string;
  onChangeCountry: (code: string) => void;
  onChangeProvince: (id: string | null) => void;
  onChangeCity: (id: string | null) => void;
  onChangeCityText: (text: string) => void;
  disableCountry?: boolean;
  required?: boolean;
  errors?: { country?: string; province?: string; city?: string };
  labels: GeoLocationSelectLabels;
  /** Optional DOM id for the country, province, and city inputs. Useful for
   *  scroll-to-error and label-for associations. The ids attached are
   *  `${ids.country}` / `${ids.province}` / `${ids.city}`. */
  ids?: { country?: string; province?: string; city?: string };
}

export function GeoLocationSelect({
  countryCode,
  provinceId,
  cityId,
  cityText,
  onChangeCountry,
  onChangeProvince,
  onChangeCity,
  onChangeCityText,
  disableCountry = false,
  required = false,
  errors,
  labels,
  ids,
}: GeoLocationSelectProps) {
  const { i18n } = useTranslation();
  const [geoCountries, setGeoCountries] = useState<GeoCountry[]>([]);
  const [provinces, setProvinces] = useState<{ value: string; label: string }[]>([]);
  const [cities, setCities] = useState<{ value: string; label: string }[]>([]);
  const [citySearch, setCitySearch] = useState("");
  // Remembers the label of the selected city so the Select keeps showing the name
  // even after the search query filters it out of the loaded `cities` batch.
  const [selectedCityLabel, setSelectedCityLabel] = useState<string | null>(null);

  const isSpain = countryCode === SPAIN_ALPHA3;

  useEffect(() => {
    const client = ApiClient.createFromBrowser(createApiConfig());
    client
      .get<{ countries: GeoCountry[] }>("/geo/countries")
      .then((res) => setGeoCountries(res.data.countries))
      .catch(() => {});
  }, []);

  const countryOptions = useMemo(() => {
    const isEs = i18n.language.startsWith("es");
    return geoCountries
      .map((c) => ({ value: c.alpha_3, label: isEs ? c.name_es : c.name }))
      .sort((a, b) => a.label.localeCompare(b.label, isEs ? "es" : "en"));
  }, [geoCountries, i18n.language]);

  useEffect(() => {
    if (!isSpain) {
      setProvinces([]);
      return;
    }
    const client = ApiClient.createFromBrowser(createApiConfig());
    client
      .get<{ provinces: GeoProvince[] }>("/geo/provinces", { size: "100" })
      .then((res) => {
        setProvinces(
          res.data.provinces
            .map((p) => ({ value: p.id, label: p.name }))
            .sort((a, b) => a.label.localeCompare(b.label, "es")),
        );
      })
      .catch(() => setProvinces([]));
  }, [isSpain]);

  useEffect(() => {
    if (!provinceId) {
      setCities([]);
      return;
    }
    const params: Record<string, string> = {
      province_id: provinceId,
      size: "50",
    };
    if (citySearch.trim()) {
      params.query = citySearch.trim();
    }
    const timer = setTimeout(() => {
      const client = ApiClient.createFromBrowser(createApiConfig());
      client
        .get<{ cities: GeoCity[] }>("/geo/cities", params)
        .then((res) => {
          setCities(res.data.cities.map((c) => ({ value: c.id, label: c.name })));
        })
        .catch(() => setCities([]));
    }, 300);
    return () => clearTimeout(timer);
  }, [provinceId, citySearch]);

  // Whenever the loaded cities batch contains the currently selected cityId, cache
  // its label so we can keep showing the name if a later search filters it out.
  useEffect(() => {
    if (!cityId) {
      setSelectedCityLabel(null);
      return;
    }
    const match = cities.find((c) => c.value === cityId);
    if (match) setSelectedCityLabel(match.label);
  }, [cities, cityId]);

  // Keep the search box text (`citySearch`) in sync with the committed `cityId`.
  // The city Select is searchable with async, debounced options. If we leave its
  // search value uncontrolled, Mantine's internal search state drifts out of sync
  // with `value` as the options list is swapped under it — so picking a city can
  // commit the wrong option and leave the "required" error stuck. Controlling
  // `searchValue` (mirroring how KeyboardSelect handles this) makes selection
  // deterministic. This effect handles externally-set values (edit forms that
  // preselect a city, parent resets); selection itself is synced in `onChange`.
  const lastSyncedCityId = useRef<string | null>(null);
  useEffect(() => {
    if (!cityId) {
      lastSyncedCityId.current = null;
      return;
    }
    if (cityId === lastSyncedCityId.current) return;
    const label =
      cities.find((c) => c.value === cityId)?.label || selectedCityLabel || cityText;
    if (label) {
      setCitySearch(label);
      lastSyncedCityId.current = cityId;
    }
  }, [cityId, cities, selectedCityLabel, cityText]);

  // The Select needs the chosen option to be present in `data` to render its label
  // instead of the raw value (UUID). If it's missing — either because the user is
  // searching or because we're editing an existing record before the cities loaded —
  // inject a placeholder using the cached label, falling back to cityText.
  const citiesWithPreselected = useMemo(() => {
    if (!cityId || cities.some((c) => c.value === cityId)) return cities;
    const label = selectedCityLabel || cityText || cityId;
    return [{ value: cityId, label }, ...cities];
  }, [cities, cityId, cityText, selectedCityLabel]);

  return (
    <>
      <KeyboardSelect
        id={ids?.country}
        label={labels.country}
        placeholder={labels.countryPlaceholder}
        data={countryOptions}
        filter={unaccentFilter}
        value={countryCode}
        onChange={(v) => onChangeCountry(v ?? "")}
        disabled={disableCountry}
        required={required}
        error={errors?.country}
      />
      {isSpain && (
        <KeyboardSelect
          id={ids?.province}
          label={labels.province}
          placeholder={labels.provincePlaceholder}
          data={provinces}
          clearable
          filter={unaccentFilter}
          value={provinceId}
          onChange={(v) => {
            onChangeProvince(v);
            onChangeCity(null);
            setCitySearch("");
            setCities([]);
          }}
          required={required}
          error={errors?.province}
        />
      )}
      <SimpleGrid cols={{ base: 1, xs: 1 }} spacing="md">
        {isSpain ? (
          <Select
            id={ids?.city}
            label={labels.city}
            placeholder={labels.cityPlaceholder}
            data={citiesWithPreselected}
            searchable
            clearable
            value={cityId}
            searchValue={citySearch}
            onSearchChange={setCitySearch}
            filter={({ options }) => options}
            onChange={(v) => {
              onChangeCity(v);
              // Mirror the committed value back into the search box so the two
              // never drift (see lastSyncedCityId effect above).
              setCitySearch(
                v
                  ? citiesWithPreselected.find((c) => c.value === v)?.label ?? ""
                  : "",
              );
              if (v) lastSyncedCityId.current = v;
            }}
            required={required}
            error={errors?.city}
            disabled={!provinceId}
          />
        ) : (
          <TextInput
            id={ids?.city}
            label={labels.city}
            placeholder={labels.cityPlaceholder}
            value={cityText}
            onChange={(e) => onChangeCityText(e.currentTarget.value)}
            required={required}
            error={errors?.city}
          />
        )}
      </SimpleGrid>
    </>
  );
}
