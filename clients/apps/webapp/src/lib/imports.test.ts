import { describe, expect, it } from "vitest"
import { ATTEMPTS, nextStep, refusedChunk, sameFile, type Attempt } from "./imports"

const SIZE = 10

describe("the file chosen again after a reload", () => {
  const record = { name: "pinry.zip", size: SIZE, lastModified: 1 }

  it("Given the three recorded fields, Then it is the same file", () => {
    expect(sameFile(record, { ...record })).toBe(true)
  })

  it.each([{ name: "other.zip" }, { size: SIZE + 1 }, { lastModified: 2 }])(
    "Given one field that differs (%o), Then it is another file",
    (field) => {
      expect(sameFile(record, { ...record, ...field })).toBe(false)
    },
  )
})

const send = (offset: number, failures = 0): Attempt => ({ next: "SEND", offset, failures })
const close = (failures = 0): Attempt => ({ next: "COMPLETE", failures })
const stop = (code: string) => ({ next: "STOP", code })

describe("the upload loop's next step", () => {
  it("Given a chunk appended short of the file's end, Then the next one starts there", () => {
    expect(nextStep({ uploadedBytes: 4 }, send(0), SIZE)).toEqual(send(4))
  })

  it("Given the file's whole length on the server, Then the upload is completed", () => {
    expect(nextStep({ uploadedBytes: SIZE }, send(8), SIZE)).toEqual(close())
  })

  it("Given a server longer than the file, Then the upload stops: it holds another file", () => {
    expect(nextStep({ uploadedBytes: 12 }, send(8), SIZE)).toEqual(stop("ARCHIVE_LONGER_THAN_FILE"))
  })

  it("Given an offset mismatch, Then the next chunk starts at the length the server names", () => {
    const answer = refusedChunk(409, { code: "IMPORT_CHUNK_OFFSET_MISMATCH", currentLength: 6 })
    expect(nextStep(answer, send(4, 2), SIZE)).toEqual(send(6))
  })

  it("Given an offset mismatch past the file's end, Then the upload stops as for a 200", () => {
    const answer = refusedChunk(409, { code: "IMPORT_CHUNK_OFFSET_MISMATCH", currentLength: 12 })
    expect(nextStep(answer, send(4), SIZE)).toEqual(stop("ARCHIVE_LONGER_THAN_FILE"))
  })

  it("Given an offset mismatch naming no length, Then there is nothing to resume from", () => {
    const answer = refusedChunk(409, { code: "IMPORT_CHUNK_OFFSET_MISMATCH" })
    expect(nextStep(answer, send(4), SIZE)).toEqual(stop("IMPORT_CHUNK_OFFSET_MISMATCH"))
  })

  it("Given a request that never reached the API, Then the same offset is sent again", () => {
    expect(nextStep(null, send(4), SIZE)).toEqual(send(4, 1))
  })

  it("Given a server error other than 507, Then it counts as a failed attempt", () => {
    expect(nextStep(refusedChunk(503, null), send(4, 1), SIZE)).toEqual(send(4, 2))
  })

  it("Given the last attempt failing, Then the upload pauses for the user", () => {
    expect(nextStep(null, send(4, ATTEMPTS - 1), SIZE)).toEqual({ next: "PAUSE" })
  })

  it("Given a 507, Then the upload stops with its code: waiting frees no disk", () => {
    const answer = refusedChunk(507, { code: "IMPORT_INSUFFICIENT_STORAGE" })
    expect(nextStep(answer, send(4), SIZE)).toEqual(stop("IMPORT_INSUFFICIENT_STORAGE"))
  })

  it("Given the archive bound, Then the upload stops with its code", () => {
    const answer = refusedChunk(413, { code: "IMPORT_ARCHIVE_TOO_LARGE" })
    expect(nextStep(answer, send(4), SIZE)).toEqual(stop("IMPORT_ARCHIVE_TOO_LARGE"))
  })

  it("Given a bodyless 413, Then the chunk was too large for something in front of the API", () => {
    const answer = refusedChunk(413, "Request Entity Too Large")
    expect(nextStep(answer, send(4), SIZE)).toEqual(stop("CHUNK_TOO_LARGE"))
  })

  it("Given any other refusal, Then the upload stops with its code", () => {
    const answer = refusedChunk(404, { code: "IMPORT_NOT_FOUND" })
    expect(nextStep(answer, send(4), SIZE)).toEqual(stop("IMPORT_NOT_FOUND"))
  })

  it.each([send(4), close()])(
    "Given an import that no longer awaits its archive (%o), Then the server's row says the rest",
    (attempt) => {
      const answer = refusedChunk(409, { code: "IMPORT_NOT_AWAITING_ARCHIVE" })
      expect(nextStep(answer, attempt, SIZE)).toEqual({ next: "DONE" })
    },
  )

  it("Given the close accepted, Then the upload is done", () => {
    expect(nextStep({ uploadedBytes: SIZE }, close(), SIZE)).toEqual({ next: "DONE" })
  })

  it("Given a close that never reached the API, Then it is sent again, then paused", () => {
    expect(nextStep(null, close(), SIZE)).toEqual(close(1))
    expect(nextStep(null, close(ATTEMPTS - 1), SIZE)).toEqual({ next: "PAUSE" })
  })

  it("Given a close refused on its merits, Then the upload stops with its code", () => {
    const answer = refusedChunk(409, { code: "IMPORT_ARCHIVE_EMPTY" })
    expect(nextStep(answer, close(), SIZE)).toEqual(stop("IMPORT_ARCHIVE_EMPTY"))
  })
})
