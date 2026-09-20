import { m } from "./paraglide/messages.js"

/**
 * One sentence per `DownloadReason`. The contract leaves `reasonCode` a plain string on purpose,
 * a response enum gaining a value being a break (specification 2026-09-10, 4.10), so a code this
 * bundle does not know is a miss here rather than a compile error.
 */
const REASONS: Record<string, () => string> = {
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
 * the fallback for a reason this bundle has no key for.
 */
export function downloadReason(
  reasonCode: string | null | undefined,
  message: string | null | undefined,
): string | null {
  return REASONS[reasonCode ?? ""]?.() ?? message ?? null
}
