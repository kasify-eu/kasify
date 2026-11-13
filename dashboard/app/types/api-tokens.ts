export type ApiToken = {
  id: string;
  name: string;
  token_prefix: string;
  last_used_at: string | null;
  expires_at: string | null;
  created_at: string;
};

export type CreateApiTokenRequest = {
  name: string;
  expires_in_days?: number | null;
};

export type CreateApiTokenResponse = {
  id: string;
  name: string;
  token: string;
  token_prefix: string;
  expires_at: string | null;
  created_at: string;
};
