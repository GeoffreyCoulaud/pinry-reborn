import type { RefusalCode } from "@pinry-reborn/auth"
import { m } from "./paraglide/messages.js"

/** What the two password-backed writes can refuse with, read from the contract. */
type PasswordRefusalCode = RefusalCode<"/api/v1/me/password", "put"> | RefusalCode<"/api/v1/me", "delete">

/**
 * One sentence per refusal the two password-backed writes can answer with. Keyed by the code and
 * not the status: `PASSWORD_CHANGED_TOO_SOON` and `TOO_MANY_AUTHENTICATION_ATTEMPTS` are both 429
 * and the user can act on only one of them (specification 2026-09-22, decision D). A key the
 * contract stops declaring fails the typecheck.
 */
const REFUSALS = {
  REAUTHENTICATION_FAILED: m.password_wrong,
  PASSWORD_PREVIOUSLY_USED: m.password_previously_used,
  PASSWORD_CHANGED_TOO_SOON: m.password_changed_too_soon,
  PASSWORD_CHANGE_COLLISION: m.password_change_collision,
  TOO_MANY_AUTHENTICATION_ATTEMPTS: m.too_many_attempts,
} satisfies Partial<Record<PasswordRefusalCode, () => string>>

// `hasOwn` and not `in`: the code is the server's string, and `constructor` would otherwise answer
// with an object, which React throws on as a child.
function hasSentence(code: string): code is keyof typeof REFUSALS {
  return Object.hasOwn(REFUSALS, code)
}

/**
 * Why the write was refused, in the reader's own language. `UNSUPPORTED_REAUTHENTICATION_FACTOR`
 * falls to the general sentence with every unknown code: it says the encoder above has a defect,
 * which is nothing to tell the user about.
 */
export function passwordRefusal(code: string | null): string {
  return code !== null && hasSentence(code) ? REFUSALS[code]() : m.account_refused()
}
