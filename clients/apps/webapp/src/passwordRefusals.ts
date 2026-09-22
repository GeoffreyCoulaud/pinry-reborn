import { m } from "./paraglide/messages.js"

/**
 * One sentence per refusal the two password-backed writes can answer with. Keyed by the code and
 * not the status: `PASSWORD_CHANGED_TOO_SOON` and `TOO_MANY_AUTHENTICATION_ATTEMPTS` are both 429
 * and the user can act on only one of them (specification 2026-09-22, decision D).
 */
const REFUSALS: Record<string, () => string> = {
  REAUTHENTICATION_FAILED: m.password_wrong,
  PASSWORD_PREVIOUSLY_USED: m.password_previously_used,
  PASSWORD_CHANGED_TOO_SOON: m.password_changed_too_soon,
  PASSWORD_CHANGE_COLLISION: m.password_change_collision,
  TOO_MANY_AUTHENTICATION_ATTEMPTS: m.too_many_attempts,
}

/**
 * Why the write was refused, in the reader's own language. `UNSUPPORTED_REAUTHENTICATION_FACTOR`
 * falls to the general sentence with every unknown code: it says the encoder above has a defect,
 * which is nothing to tell the user about.
 */
export function passwordRefusal(code: string | null): string {
  const key = code ?? ""
  // `hasOwn` and not the lookup alone: the code is the server's string, and `constructor` would
  // otherwise answer with an object, which React throws on as a child.
  return (Object.hasOwn(REFUSALS, key) ? REFUSALS[key]?.() : undefined) ?? m.account_refused()
}
