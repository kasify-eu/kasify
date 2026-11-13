import type { GeoCity, GeoCountry, GeoProvince } from "../../../app/types/geo";

// A deliberately small country list — enough to exercise Spanish vs.
// non-Spanish branches (second surname, residence province/city) without the
// real ~250-entry dataset slowing the combobox down in tests.
export const countries: GeoCountry[] = [
  { id: "c-esp", name: "Spain", name_es: "España", alpha_2: "ES", alpha_3: "ESP" },
  { id: "c-fra", name: "France", name_es: "Francia", alpha_2: "FR", alpha_3: "FRA" },
  {
    id: "c-usa",
    name: "United States",
    name_es: "Estados Unidos",
    alpha_2: "US",
    alpha_3: "USA",
  },
];

export const provinces: GeoProvince[] = [
  { id: "p-madrid", name: "Madrid" },
  { id: "p-malaga", name: "Málaga" },
];

// Cities keyed by province id, mirroring the backend's province_id filter.
export const citiesByProvince: Record<string, GeoCity[]> = {
  "p-madrid": [
    { id: "city-madrid", name: "Madrid" },
    { id: "city-alcala", name: "Alcalá de Henares" },
  ],
  "p-malaga": [
    { id: "city-malaga", name: "Málaga" },
    { id: "city-marbella", name: "Marbella" },
  ],
};
