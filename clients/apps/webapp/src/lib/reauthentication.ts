/**
 * The `X-Reauthentication` header the API's parser reads as `password <base64url(password)>`.
 * `btoa` writes standard base64 and the API decodes with `Base64.getUrlDecoder()`, which refuses a
 * `+` or a `/` outright; the padding it writes is accepted either way, so it stays
 * (specification 2026-09-22, decision G).
 */
export function passwordFactor(password: string): string {
  const bytes = new TextEncoder().encode(password)
  const latin1 = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("")
  return `password ${btoa(latin1).replaceAll("+", "-").replaceAll("/", "_")}`
}
