export type Locale = "en" | "es";

export type Account = {
  id: string;
  account_id: string;
  name: string;
  last_name: string;
  email: string;
  locale: Locale;
};
