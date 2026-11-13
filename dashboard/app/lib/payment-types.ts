import { PAYMENT_TYPES, type PaymentType } from "~/types/bookings";

type Translate = (key: string) => string;

/** Localized label for a payment type value (e.g. "credit_card" → "Credit card"). */
export function paymentTypeLabel(t: Translate, value?: string | null): string {
  if (!value) return "";
  return t(`bookingDetails.paymentTypes.${value}`);
}

/** Optional helper text shown under a payment type option; empty when none is defined. */
export function paymentTypeDescription(t: Translate, value?: string | null): string {
  if (!value) return "";
  const key = `bookingDetails.paymentTypeDescriptions.${value}`;
  const translated = t(key);
  return translated === key ? "" : translated;
}

/** Select-ready options for every payment type, localized via i18n. */
export function paymentTypeOptions(t: Translate): { value: PaymentType; label: string }[] {
  return PAYMENT_TYPES.map((value) => ({ value, label: paymentTypeLabel(t, value) }));
}
