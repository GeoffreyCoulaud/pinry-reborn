import { m } from "./paraglide/messages.js"

/**
 * One sentence per `failureCode` the user can act on. The contract leaves it an open string, an
 * unknown code having a correct fallback (specification 2026-09-25, decision I).
 */
const FAILURES = {
  DISK_FULL: m.failure_disk_full,
  BUILD_FAILED: m.failure_build_failed,
  EXPORT_INTERRUPTED: m.failure_interrupted,
}

// `hasOwn` and not `in`: the code is the server's string, and `constructor` would answer otherwise.
function hasSentence(code: string): code is keyof typeof FAILURES {
  return Object.hasOwn(FAILURES, code)
}

/** Why an export stopped, or the general sentence for a code this bundle does not know. */
export function dataFailure(code: string | null): string {
  return code !== null && hasSentence(code) ? FAILURES[code]() : m.failure_unknown()
}
