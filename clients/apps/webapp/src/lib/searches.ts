/**
 * A search parameter is whatever the address bar holds, so a term the API would refuse becomes no
 * term at all: `q=` is a caller that did not mean to search and the route answers 400 to it
 * (specification 2026-09-21, decision C).
 */
export function searchTermOr(value: unknown): string | undefined {
  const term = typeof value === "string" ? value.trim() : ""
  return term === "" ? undefined : term
}
