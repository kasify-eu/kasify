// DNI/NIE "número de soporte" formats per the Spanish Ministry of Justice
// ("Cómo introducir el número de soporte"). Both are always 9 characters
// (shorter NIE numbers are zero-padded on the left):
//  - DNI electrónico (IDESP, front of card): 3 letters + 6 digits, e.g. ABC123456.
//  - NIE (TIE / permiso de residencia): letter E + 8 digits, e.g. E12345678.
//  - NIE (certificado de registro de ciudadano de la Unión): letter C + 8 digits, e.g. C01234567.
export const DNI_SUPPORT_REGEX = /^[A-Z]{3}[0-9]{6}$/;
export const NIE_SUPPORT_REGEX = /^[EC][0-9]{8}$/;

/**
 * Validates a DNI/NIE support number ("número de soporte") for the given document type and
 * document number. Returns the i18next key of the error to display, or `null` when valid.
 *
 * Mirrors the backend rule in
 * `GuestRegistrationRequest.validateDocumentSupportNumber()`; only call it for DNI/NIE
 * documents (the caller decides when a support number is applicable).
 */
export function validateDocumentSupportNumber(
  documentType: string,
  documentSupportNumber: string,
  documentNumber: string,
): string | null {
  const support = documentSupportNumber.trim().toUpperCase();
  if (!support) {
    return "checkinGuest.required";
  }
  const doc = documentNumber.trim().toUpperCase();
  if (doc && support === doc) {
    return "checkinGuest.supportNumberSameAsDocument";
  }
  if (documentType === "DNI" && !DNI_SUPPORT_REGEX.test(support)) {
    return "checkinGuest.invalidDniSupportNumber";
  }
  if (documentType === "NIE" && !NIE_SUPPORT_REGEX.test(support)) {
    return "checkinGuest.invalidNieSupportNumber";
  }
  return null;
}
