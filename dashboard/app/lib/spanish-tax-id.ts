const DNI_REGEX = /^[0-9]{8}[A-Z]$/;
const NIE_REGEX = /^[XYZ][0-9]{7}[A-Z]$/;
// K (minors under 14), L (Spaniards resident abroad) and M (foreigners without NIE) are personal
// NIFs with the same mod-23 control letter as a DNI, computed over the 7 digits.
const KLM_NIF_REGEX = /^[KLM][0-9]{7}[A-Z]$/;
// CIF organization letters — K/L/M are personal NIF prefixes, not CIF prefixes.
const CIF_REGEX = /^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$/;
const DNI_CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

/**
 * Spanish tax id (NIF/DNI incl. K/L/M, NIE or CIF). Mirrors the backend rule in
 * NewPropertyRequest.kt: personal ids are checksum-validated (mod-23 control letter),
 * CIF is format-only.
 */
export function isValidSpanishTaxId(value: string): boolean {
  const normalized = value.toUpperCase().replace(/[\s-]/g, "");
  if (DNI_REGEX.test(normalized)) {
    return (
      normalized[8] ===
      DNI_CONTROL_LETTERS[parseInt(normalized.slice(0, 8), 10) % 23]
    );
  }
  if (NIE_REGEX.test(normalized)) {
    const number = parseInt(
      `${"XYZ".indexOf(normalized[0])}${normalized.slice(1, 8)}`,
      10,
    );
    return normalized[8] === DNI_CONTROL_LETTERS[number % 23];
  }
  if (KLM_NIF_REGEX.test(normalized)) {
    return (
      normalized[8] ===
      DNI_CONTROL_LETTERS[parseInt(normalized.slice(1, 8), 10) % 23]
    );
  }
  return CIF_REGEX.test(normalized);
}
