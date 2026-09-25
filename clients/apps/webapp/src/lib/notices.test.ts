import type { Schemas } from "@pinry-reborn/auth"
import { describe, expect, it } from "vitest"
import { NOTICE_MS, dataNotices } from "./notices"

type Export = Schemas["UserDataExportOutputDto"]
type Import = Schemas["UserDataImportOutputDto"]

const NOW = Date.parse("2026-09-25T12:00:00Z")
const at = (offsetMs: number) => new Date(NOW + offsetMs).toISOString()

/** As the API emits one: no failure path stamps `completedAt` (specification 2026-09-25, section 2). */
function exportRow(state: Export["state"], fields: Partial<Export> = {}): Export {
  return {
    id: "export",
    state,
    requestedAt: at(-3_600_000),
    completedAt: null,
    expiresAt: null,
    byteSize: null,
    mediaType: null,
    sha256: null,
    failureCode: null,
    formatVersion: 1,
    ...fields,
  }
}

function importRow(state: Import["state"], fields: Partial<Import> = {}): Import {
  return {
    id: "import",
    state,
    requestedAt: at(-3_600_000),
    uploadedBytes: 10,
    byteSize: 10,
    archiveCompletedAt: null,
    startedAt: null,
    completedAt: null,
    formatVersion: null,
    announcedPins: null,
    processedPins: 0,
    createdPins: 0,
    skippedPins: 0,
    createdBoards: 0,
    skippedBoards: 0,
    createdTags: 0,
    skippedTags: 0,
    issueCount: 0,
    issueDetailTruncated: false,
    failureCode: null,
    ...fields,
  }
}

const ready = (expiresInMs: number) =>
  exportRow("READY", { completedAt: at(-60_000), expiresAt: at(expiresInMs) })
const completed = (agoMs: number) => importRow("COMPLETED", { completedAt: at(-agoMs) })

describe("which data notices the task centre shows", () => {
  it("Given a ready export, Then it shows before `expiresAt` and not after", () => {
    expect(dataNotices(ready(60_000), null, [], NOW).export?.id).toBe("export")
    expect(dataNotices(ready(0), null, [], NOW).export).toBeNull()
  })

  it("Given a completed import, Then it shows for 24 hours after `completedAt` and not after", () => {
    expect(dataNotices(null, completed(NOTICE_MS - 1), [], NOW).import?.id).toBe("import")
    expect(dataNotices(null, completed(NOTICE_MS), [], NOW).import).toBeNull()
  })

  it("Given a success with no instant to count from, Then it shows nothing", () => {
    expect(dataNotices(exportRow("READY"), importRow("COMPLETED"), [], NOW)).toEqual({
      export: null,
      import: null,
      dismissed: [],
    })
  })

  it("Given failures, whose rows carry no `completedAt`, Then they show at any time", () => {
    const later = NOW + 365 * NOTICE_MS
    const failed = dataNotices(exportRow("FAILED"), importRow("FAILED"), [], later)
    expect([failed.export?.state, failed.import?.state]).toEqual(["FAILED", "FAILED"])
    expect(dataNotices(null, importRow("ABANDONED"), [], later).import?.state).toBe("ABANDONED")
  })

  it.each(["PENDING", "EXPIRED", "DELETED", "SUPERSEDED"] as const)(
    "Given an export %s, Then it leaves no notice",
    (state) => {
      expect(dataNotices(exportRow(state, { expiresAt: at(60_000) }), null, [], NOW).export).toBeNull()
    },
  )

  it.each(["AWAITING_ARCHIVE", "PENDING", "RUNNING", "CANCELLED"] as const)(
    "Given an import %s, Then it leaves no notice: a cancellation is the user's own gesture",
    (state) => {
      expect(dataNotices(null, importRow(state, { completedAt: at(0) }), [], NOW).import).toBeNull()
    },
  )

  it("Given dismissed notices, Then they stay hidden and their ids are kept", () => {
    const shown = dataNotices(exportRow("FAILED"), completed(0), ["export", "import"], NOW)
    expect(shown).toEqual({ export: null, import: null, dismissed: ["export", "import"] })
  })

  it("Given a dismissal whose notice's bound has passed or whose row is gone, Then it is dropped", () => {
    const shown = dataNotices(ready(0), completed(0), ["export", "import", "older"], NOW)
    expect(shown.dismissed).toEqual(["import"])
  })
})
