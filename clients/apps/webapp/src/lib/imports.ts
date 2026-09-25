import { refusalCode } from "./refusals"

/** What a chunk's `PUT` or the close came back with: the upload's length, a refusal, or `null`. */
export type ChunkAnswer =
  | { uploadedBytes: number }
  | { status: number; code: string | null; currentLength: number | null }
  | null

/** What the upload does next (specification 2026-09-25, decision F4, steps 2 to 5). */
export type UploadStep =
  | { next: "SEND"; offset: number; failures: number }
  | { next: "COMPLETE"; failures: number }
  | { next: "PAUSE" }
  | { next: "STOP"; code: string | null }
  | { next: "DONE" }

/** A request the upload sends again until it is answered: a chunk, or the close. */
export type Attempt = Extract<UploadStep, { failures: number }>

/** What an upload records of its file, so a reload can ask for the same one again. */
export interface FileIdentity {
  name: string
  size: number
  lastModified: number
}

/** No hash: it would read the whole file, and the check guards against a mistake. */
export function sameFile(record: FileIdentity, file: FileIdentity): boolean {
  return (
    record.name === file.name &&
    record.size === file.size &&
    record.lastModified === file.lastModified
  )
}

/** Attempts at one offset before the upload waits for the user, `RETRY_MS` apart. */
export const ATTEMPTS = 5
export const RETRY_MS = 5_000

/** A refused `PUT`, read from a body that may be a proxy's rather than the contract's. */
export function refusedChunk(status: number, body: unknown): ChunkAnswer {
  const length = (body as { currentLength?: unknown } | null)?.currentLength
  const currentLength = typeof length === "number" ? length : null
  return { status, code: refusalCode(body), currentLength }
}

/** Where the server's length leaves a file of `size` bytes. */
function resumedAt(length: number, size: number): UploadStep {
  if (length === size) return { next: "COMPLETE", failures: 0 }
  // The server holds bytes this file does not have, so it is not the file the upload started with.
  if (length > size) return { next: "STOP", code: "ARCHIVE_LONGER_THAN_FILE" }
  return { next: "SEND", offset: length, failures: 0 }
}

export function nextStep(answer: ChunkAnswer, attempt: Attempt, size: number): UploadStep {
  if (answer !== null && "uploadedBytes" in answer) {
    return attempt.next === "COMPLETE" ? { next: "DONE" } : resumedAt(answer.uploadedBytes, size)
  }
  // How a cut chunk resumes: the file on the server is the truth, and the row's figure can lag it.
  if (answer?.code === "IMPORT_CHUNK_OFFSET_MISMATCH" && answer.currentLength !== null) {
    return resumedAt(answer.currentLength, size)
  }
  // Another tab, or a close whose answer was lost, got there first: the server's row says the rest.
  if (answer?.code === "IMPORT_NOT_AWAITING_ARCHIVE") return { next: "DONE" }
  if (answer === null || (answer.status >= 500 && answer.status !== 507)) {
    if (attempt.failures + 1 >= ATTEMPTS) return { next: "PAUSE" }
    return { ...attempt, failures: attempt.failures + 1 }
  }
  // A bodyless 413 is a proxy's lower limit, and `BODY_TOO_LARGE` the API's: the chunk is refused.
  if (answer.status === 413 && answer.code !== "IMPORT_ARCHIVE_TOO_LARGE") {
    return { next: "STOP", code: "CHUNK_TOO_LARGE" }
  }
  return { next: "STOP", code: answer.code }
}
