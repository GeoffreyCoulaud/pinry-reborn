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
function hasSentence<Table extends object>(
  table: Table,
  code: string | null,
): code is Extract<keyof Table, string> {
  return code !== null && Object.hasOwn(table, code)
}

/** Why the export was refused. `UNSUPPORTED_REAUTHENTICATION_FACTOR` is the encoder's defect. */
export function exportRefusal(code: string | null): string {
  return hasSentence(EXPORT_REFUSALS, code) ? EXPORT_REFUSALS[code]() : m.account_refused()
}

type ImportRefusalCode =
  | RefusalCode<"/api/v1/me/imports", "post">
  | RefusalCode<"/api/v1/me/imports/{id}/archive", "put">
  | RefusalCode<"/api/v1/me/imports/{id}/archive/complete", "post">

/** The upload's refusals, the two `lib/imports.ts` names itself included. */
type UploadStopCode = ImportRefusalCode | "CHUNK_TOO_LARGE" | "ARCHIVE_LONGER_THAN_FILE"

const IMPORT_REFUSALS = {
  IMPORT_ALREADY_IN_PROGRESS: m.import_in_progress,
  IMPORT_ARCHIVE_TOO_LARGE: m.import_too_large,
  IMPORT_INSUFFICIENT_STORAGE: m.import_no_space,
  IMPORT_ARCHIVE_EMPTY: m.import_empty,
  CHUNK_TOO_LARGE: m.import_chunk_too_large,
  ARCHIVE_LONGER_THAN_FILE: m.import_other_file,
} satisfies Partial<Record<UploadStopCode, () => string>>

/** Why the import or its upload stopped, or the general sentence for a code this bundle lacks. */
export function importRefusal(code: string | null): string {
  return hasSentence(IMPORT_REFUSALS, code) ? IMPORT_REFUSALS[code]() : m.account_refused()
}
