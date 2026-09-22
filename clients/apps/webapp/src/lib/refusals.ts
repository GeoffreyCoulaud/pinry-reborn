/**
 * The `code` the API's `ProblemDetail` carries, which is what the screens read: two refusals share
 * a status and only the code tells them apart. `unknown` because the contract declares no body for
 * these statuses, so the generated types say `error` holds nothing while openapi-fetch parses one
 * anyway (specification 2026-09-22, section 8).
 */
export function refusalCode(error: unknown): string | null {
  if (typeof error !== "object" || error === null) return null
  const code = (error as { code?: unknown }).code
  return typeof code === "string" ? code : null
}
