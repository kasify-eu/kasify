export interface GeoCountry {
  id: string;
  name: string;
  name_es: string;
  alpha_2: string;
  alpha_3: string;
}

export interface GeoProvince {
  id: string;
  name: string;
}

export interface GeoCity {
  id: string;
  name: string;
}

export const SPAIN_ALPHA3 = "ESP";
