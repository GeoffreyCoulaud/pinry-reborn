import type { Known } from "@pinry-reborn/auth"
import { m } from "./paraglide/messages.js"

/**
 * One sentence per reason the contract knows: `reasonCode` is an `x-extensible-enum`, so a reason
 * the server adds fails `tsc` here until it has one (docs/adr/0044-a-response-code-declares-its-set.md).
 */
const REASONS: Record<Known<"DownloadReasonDto">, () => string> = {
  URL_NOT_ALLOWED: m.reason_url_not_allowed,
  UNREACHABLE: m.reason_unreachable,
  ACCESS_DENIED: m.reason_access_denied,
  NOT_FOUND: m.reason_not_found,
  TOO_LARGE: m.reason_too_large,
  INVALID_IMAGE: m.reason_invalid_image,
  TOO_MANY_PIXELS: m.reason_too_many_pixels,
  INTERNAL_ERROR: m.reason_internal_error,
  FETCH_FAILED: m.reason_fetch_failed,
}

/**
 * Why a download failed, in the reader's own language. `reasonCode` is the machine value
 * specification 4.8 asks the task to offer; `message` is the server's English sentence, kept as
 * the fallback for a reason a newer server sends and this bundle has no key for.
 */
export function downloadReason(
  reasonCode: string | null | undefined,
  message: string | null | undefined,
): string | null {
  const key = reasonCode ?? ""
  // `hasOwn` and not the lookup alone: the code is the server's string, and `Object.prototype`
  // would otherwise answer for a dozen names this table never wrote.
  return (Object.hasOwn(REASONS, key) ? REASONS[key as keyof typeof REASONS]() : undefined) ?? message ?? null
}
