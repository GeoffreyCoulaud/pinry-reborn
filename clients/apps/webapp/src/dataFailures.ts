import type { Known } from "@pinry-reborn/auth"
import { m } from "./paraglide/messages.js"

type Sentences<Code extends string> = Record<Code, () => string>

/**
 * One entry per failure the contract knows, and none for a gone cause, a `GONE` row rendering no sentence:
 * both `reasonCode`s are `x-extensible-enum`s, so a reason the server adds fails `tsc` here (ADR 0044).
 */
type ExportFailure = Exclude<Known<"UserDataExportReasonDto">, "EXPIRED" | "DELETED" | "SUPERSEDED">

const EXPORT_REASONS: Sentences<ExportFailure> = {
  USER_GONE: m.failure_unknown,
  DISK_FULL: m.failure_disk_full,
  BUILD_FAILED: m.failure_build_failed,
  EXPORT_INTERRUPTED: m.failure_interrupted,
}

const IMPORT_REASONS: Sentences<Known<"UserDataImportReasonDto">> = {
  USER_GONE: m.failure_unknown,
  IMPORT_FAILED: m.failure_unknown,
  ARCHIVE_UNREADABLE: m.failure_archive_unreadable,
  MANIFEST_MISSING: m.failure_manifest_missing,
  UNSUPPORTED_FORMAT_VERSION: m.failure_unsupported_format_version,
  IMPORT_INTERRUPTED: m.failure_interrupted,
}

// `hasOwn` and not `in`: the code is the server's string, and `constructor` would answer otherwise.
function sentence<Code extends string>(table: Sentences<Code>, code: string | null): string {
  return (code !== null && Object.hasOwn(table, code) ? table[code as Code] : m.failure_unknown)()
}

/** Why an export failed, or the general sentence for a reason a newer server sends. */
export const exportFailure = (code: string | null) => sentence(EXPORT_REASONS, code)

/** Why an import failed, or the general sentence for a reason a newer server sends. */
export const importFailure = (code: string | null) => sentence(IMPORT_REASONS, code)
