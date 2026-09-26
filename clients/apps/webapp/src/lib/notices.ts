import type { Schemas } from "@pinry-reborn/auth"

type Export = Pick<Schemas["UserDataExportOutputDto"], "id" | "state" | "expiresAt">
type Import = Pick<Schemas["UserDataImportOutputDto"], "id" | "state" | "completedAt">

/** How long a completed import's notice waits to be read (specification 2026-09-25, decision G3). */
export const NOTICE_MS = 24 * 60 * 60 * 1000

const before = (instant: string | null, now: number, marginMs = 0) =>
  instant !== null && now < Date.parse(instant) + marginMs

// Keyed by the contract's closed unions, so a state added there fails the typecheck here. A
// failure has no bound: the API stamps no instant on one to count from.
const EXPORT_NOTICES: Record<Export["state"], (row: Export, now: number) => boolean> = {
  PENDING: () => false,
  READY: (row, now) => before(row.expiresAt, now),
  FAILED: () => true,
  GONE: () => false,
}

const IMPORT_NOTICES: Record<Import["state"], (row: Import, now: number) => boolean> = {
  AWAITING_ARCHIVE: () => false,
  PENDING: () => false,
  RUNNING: () => false,
  COMPLETED: (row, now) => before(row.completedAt, now, NOTICE_MS),
  FAILED: () => true,
  CANCELLED: () => false,
  ABANDONED: () => true,
}

/**
 * The latest export's and import's notices still to show, and the dismissed ids worth keeping:
 * those of a notice that would show otherwise, so a dismissal goes once its bound has passed.
 */
export function dataNotices<E extends Export, I extends Import>(
  latestExport: E | null,
  latestImport: I | null,
  dismissed: readonly string[],
  now: number,
) {
  const exported = latestExport !== null && EXPORT_NOTICES[latestExport.state](latestExport, now)
  const imported = latestImport !== null && IMPORT_NOTICES[latestImport.state](latestImport, now)
  const speaking = [exported ? latestExport.id : null, imported ? latestImport.id : null]
  const kept = dismissed.filter((id) => speaking.includes(id))
  return {
    export: exported && !kept.includes(latestExport.id) ? latestExport : null,
    import: imported && !kept.includes(latestImport.id) ? latestImport : null,
    dismissed: kept,
  }
}
