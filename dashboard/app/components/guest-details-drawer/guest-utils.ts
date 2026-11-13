import type { Guest } from "~/types/bookings";

export function calculateAge(dateOfBirth: string | null | undefined): number | null {
  if (!dateOfBirth) return null;
  const dob = new Date(dateOfBirth);
  if (isNaN(dob.getTime())) return null;
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const monthDiff = now.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < dob.getDate())) {
    age--;
  }
  return age >= 0 ? age : null;
}

export function initials(first?: string | null, last?: string | null): string {
  const a = (first || "").trim()[0] || "";
  const b = (last || "").trim()[0] || "";
  return (a + b).toUpperCase() || "—";
}

/** Stable colour tint from the guest id, so the avatar colour matches between
 *  the registered-guest list and the drawer. */
export function tintFor(id: string): "a" | "b" | "c" | "d" | "e" {
  const code = id.split("").reduce((sum, ch) => sum + ch.charCodeAt(0), 0);
  return (["a", "b", "c", "d", "e"] as const)[code % 5];
}

export function formatGuestName(guest: Pick<Guest, "first_name" | "last_name" | "second_last_name">): string {
  return [guest.first_name, guest.last_name, guest.second_last_name]
    .filter((p): p is string => Boolean(p && p.trim()))
    .join(" ");
}

export function formatDateOfBirth(dateOfBirth: string | null | undefined): string {
  if (!dateOfBirth) return "";
  const d = new Date(dateOfBirth);
  if (isNaN(d.getTime())) return dateOfBirth;
  return d.toLocaleDateString(undefined, {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

/** Emoji flag from an ISO 3166-1 alpha-2 country code (e.g. "ES" → 🇪🇸).
 *  Returns null when the code is missing or malformed. */
export function countryFlag(alpha2?: string | null): string | null {
  if (!alpha2 || alpha2.length !== 2) return null;
  const code = alpha2.toUpperCase();
  if (!/^[A-Z]{2}$/.test(code)) return null;
  const base = 0x1f1e6;
  return String.fromCodePoint(
    base + code.charCodeAt(0) - 65,
    base + code.charCodeAt(1) - 65,
  );
}

export function formatSex(sex: string | null | undefined): string | null {
  if (!sex) return null;
  const code = sex.toLowerCase();
  if (code === "m" || code === "male") return "Male";
  if (code === "f" || code === "female") return "Female";
  return sex;
}
