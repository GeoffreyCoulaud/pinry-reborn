import { m } from "./paraglide/messages.js"

/**
 * One sentence per `UserDataImportIssueKind`. The contract leaves `kind` an open string, an
 * unknown kind having a correct fallback (specification 2026-09-25, decision I).
 */
const KINDS = {
  PIN_HAS_NO_MEDIA: m.issue_pin_has_no_media,
  MEDIA_ENTRY_MISSING: m.issue_media_entry_missing,
  MEDIA_UNREADABLE: m.issue_media_unreadable,
  MEDIA_TOO_LARGE: m.issue_media_too_large,
  MEDIA_TOO_MANY_PIXELS: m.issue_media_too_many_pixels,
  MEDIA_AMBIGUOUS: m.issue_media_ambiguous,
  MEDIA_DIGEST_MISMATCH: m.issue_media_digest_mismatch,
  LINE_MALFORMED: m.issue_line_malformed,
  FIELD_INVALID: m.issue_field_invalid,
  ENTRY_PATH_INVALID: m.issue_entry_path_invalid,
  NAME_TAKEN_BY_RECYCLED: m.issue_name_taken_by_recycled,
  LINE_REJECTED: m.issue_line_rejected,
}

// `hasOwn` and not `in`: the kind is the server's string, and `constructor` would answer otherwise.
function hasSentence(kind: string): kind is keyof typeof KINDS {
  return Object.hasOwn(KINDS, kind)
}

/** What the import reported, or the general sentence for a kind this bundle does not know. */
export function importIssue(kind: string): string {
  return hasSentence(kind) ? KINDS[kind]() : m.issue_unknown()
}
