export type UserRole = "admin" | "user";

export type SessionUser = {
  id: string;
  username: string;
  email?: string;
  given_name?: string;
  family_name?: string;
  role?: UserRole;
};

export type User = {
  id: string;
  account_id: string;
  name: string;
  last_name: string;
  email: string;
  created_at: string | null;
};
