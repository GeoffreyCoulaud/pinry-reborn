import { describe, expect, it } from "vitest"
import { ATTEMPTS, nextStep, refusedChunk } from "./imports"

const SIZE = 10

const send = (offset: number, failures: number) => ({ next: "SEND", offset, failures })
const stop = (code: string) => ({ next: "STOP", code })

describe("the upload loop's next step", () => {
  it("Given a chunk appended short of the file's end, Then the next one starts there", () => {
    expect(nextStep({ uploadedBytes: 4 }, 0, SIZE, 0)).toEqual(send(4, 0))
  })

  it("Given the file's whole length on the server, Then the upload is completed", () => {
    expect(nextStep({ uploadedBytes: SIZE }, 8, SIZE, 0)).toEqual({ next: "COMPLETE" })
  })

  it("Given a server longer than the file, Then the upload stops: it holds another file", () => {
    expect(nextStep({ uploadedBytes: 12 }, 8, SIZE, 0)).toEqual(stop("ARCHIVE_LONGER_THAN_FILE"))
  })

  it("Given an offset mismatch, Then the next chunk starts at the length the server names", () => {
    const answer = refusedChunk(409, { code: "IMPORT_CHUNK_OFFSET_MISMATCH", currentLength: 6 })
    expect(nextStep(answer, 4, SIZE, 2)).toEqual(send(6, 0))
  })

  it("Given an offset mismatch past the file's end, Then the upload stops as for a 200", () => {
    const answer = refusedChunk(409, { code: "IMPORT_CHUNK_OFFSET_MISMATCH", currentLength: 12 })
    expect(nextStep(answer, 4, SIZE, 0)).toEqual(stop("ARCHIVE_LONGER_THAN_FILE"))
  })

  it("Given an offset mismatch naming no length, Then there is nothing to resume from", () => {
    const answer = refusedChunk(409, { code: "IMPORT_CHUNK_OFFSET_MISMATCH" })
    expect(nextStep(answer, 4, SIZE, 0)).toEqual(stop("IMPORT_CHUNK_OFFSET_MISMATCH"))
  })

  it("Given a request that never reached the API, Then the same offset is sent again", () => {
    expect(nextStep(null, 4, SIZE, 0)).toEqual(send(4, 1))
  })

  it("Given a server error other than 507, Then it counts as a failed attempt", () => {
    expect(nextStep(refusedChunk(503, null), 4, SIZE, 1)).toEqual(send(4, 2))
  })

  it("Given the last attempt failing, Then the upload pauses for the user", () => {
    expect(nextStep(null, 4, SIZE, ATTEMPTS - 1)).toEqual({ next: "PAUSE" })
  })

  it("Given a 507, Then the upload stops with its code: waiting frees no disk", () => {
    const answer = refusedChunk(507, { code: "IMPORT_INSUFFICIENT_STORAGE" })
    expect(nextStep(answer, 4, SIZE, 0)).toEqual(stop("IMPORT_INSUFFICIENT_STORAGE"))
  })

  it("Given the archive bound, Then the upload stops with its code", () => {
    const answer = refusedChunk(413, { code: "IMPORT_ARCHIVE_TOO_LARGE" })
    expect(nextStep(answer, 4, SIZE, 0)).toEqual(stop("IMPORT_ARCHIVE_TOO_LARGE"))
  })

  it("Given a bodyless 413, Then the chunk was too large for something in front of the API", () => {
    const answer = refusedChunk(413, "Request Entity Too Large")
    expect(nextStep(answer, 4, SIZE, 0)).toEqual(stop("CHUNK_TOO_LARGE"))
  })

  it("Given any other refusal, Then the upload stops with its code", () => {
    const answer = refusedChunk(409, { code: "IMPORT_NOT_AWAITING_ARCHIVE" })
    expect(nextStep(answer, 4, SIZE, 0)).toEqual(stop("IMPORT_NOT_AWAITING_ARCHIVE"))
  })
})
