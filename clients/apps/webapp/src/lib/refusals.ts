/**
 * The `code` the API's `ProblemDetail` carries, which is what the screens read: two refusals share
 * a status and only the code tells them apart. `unknown` because not every body is the contract's: a
 * proxy or an undeclared status answers in a shape of its own, which openapi-fetch parses anyway.
 */
export function refusalCode(error: unknown): string | null {
  if (typeof error !== "object" || error === null) return null
  const code = (error as { code?: unknown }).code
  return typeof code === "string" ? code : null
}
