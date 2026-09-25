import type { RefusalCode } from "@pinry-reborn/auth"
import { m } from "./paraglide/messages.js"

type ExportRefusalCode = RefusalCode<"/api/v1/me/exports", "post">

/**
 * One sentence per refusal of an export request, keyed by the code: `EXPORT_TOO_SOON` and
 * `TOO_MANY_AUTHENTICATION_ATTEMPTS` share a 429. A key the contract stops declaring fails the
 * typecheck.
 */
const EXPORT_REFUSALS = {
  EXPORT_ALREADY_IN_PROGRESS: m.export_in_progress,
  EXPORT_TOO_SOON: m.export_too_soon,
  REAUTHENTICATION_FAILED: m.password_wrong,
  TOO_MANY_AUTHENTICATION_ATTEMPTS: m.too_many_attempts,
} satisfies Partial<Record<ExportRefusalCode, () => string>>

// `hasOwn` and not `in`: the code is the server's string, and `constructor` would answer otherwise.
function hasSentence(code: string): code is keyof typeof EXPORT_REFUSALS {
  return Object.hasOwn(EXPORT_REFUSALS, code)
}

/** Why the export was refused. `UNSUPPORTED_REAUTHENTICATION_FACTOR` is the encoder's defect. */
export function exportRefusal(code: string | null): string {
  return code !== null && hasSentence(code) ? EXPORT_REFUSALS[code]() : m.account_refused()
}
