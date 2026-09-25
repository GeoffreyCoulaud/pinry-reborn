import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useSyncExternalStore } from "react"
import { auth, bodyOf } from "./api"
import { POLL_MS } from "./lib/downloads"
import {
  RETRY_MS,
  nextStep,
  refusedChunk,
  type ChunkAnswer,
  type FileIdentity,
  type UploadStep,
} from "./lib/imports"
import { refusalCode } from "./lib/refusals"
import { AccountRefusal } from "./me"

export type Import = Schemas["UserDataImportOutputDto"]

/** The upload in this tab: bytes sent, and whether it sends, waits on the user, or was refused. */
export interface Upload {
  importId: string
  file: File
  sent: number
  state: "SENDING" | "PAUSED" | "STOPPED"
  code: string | null
}

// The latest import, which the upload changes at each end it reaches.
const LATEST = ["imports", "latest"]

// One record per import, under its id, so a reload can ask for the same file (decision D1).
const RECORD = "pinry-import-"

/** A private window, or site data the browser blocks, throws: the import then has no record. */
function writeRecord(id: string, { name, size, lastModified }: File) {
  try {
    localStorage.setItem(RECORD + id, JSON.stringify({ name, size, lastModified }))
  } catch {
    // A reload then offers the cancel button only, as another browser would.
  }
}

export function importRecord(id: string): FileIdentity | null {
  try {
    return JSON.parse(localStorage.getItem(RECORD + id) ?? "null") as FileIdentity | null
  } catch {
    return null
  }
}

/** Only the latest import's, while it waits on its archive: no record outlives what it serves. */
function pruneRecords(latest: Import | null) {
  const kept = latest?.state === "AWAITING_ARCHIVE" ? RECORD + latest.id : null
  try {
    for (const key of Object.keys(localStorage)) {
      if (key.startsWith(RECORD) && key !== kept) localStorage.removeItem(key)
    }
  } catch {
    // Nothing could be written there either.
  }
}

/** The newest import or none, as the export's section reads its own (decision J). */
export function useLatestImport() {
  return useQuery({
    queryKey: LATEST,
    queryFn: async () => {
      const query = { pageSize: 1 }
      const answer = await auth.client.GET("/api/v1/me/imports", { params: { query } })
      const latest = bodyOf(answer, "the imports").imports[0] ?? null
      pruneRecords(latest)
      return latest
    },
    refetchInterval: (query) => {
      const state = query.state.data?.state
      return state === "PENDING" || state === "RUNNING" ? POLL_MS : false
    },
  })
}

/** One import's report, a page at a time on the cursor each page answers (decision H). */
export function useImportIssues(id: string) {
  return useInfiniteQuery({
    queryKey: ["imports", id, "issues"],
    queryFn: async ({ pageParam }) => {
      const params = { path: { id }, query: { cursor: pageParam } }
      const answer = await auth.client.GET("/api/v1/me/imports/{id}/issues", { params })
      return bodyOf(answer, "the report")
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  })
}

// The store lives above the router, so changing screen leaves the upload running (decision F4).
let upload: Upload | null = null
let controller = new AbortController()
let resume = () => {}
const listeners = new Set<() => void>()

/** The browser shows its own sentence whatever the page sets (MDN, `beforeunload` event). */
function keepPage(event: BeforeUnloadEvent) {
  event.preventDefault()
}

function publish(next: Upload | null) {
  upload = next
  if (next !== null && next.state !== "STOPPED") addEventListener("beforeunload", keepPage)
  else removeEventListener("beforeunload", keepPage)
  for (const listener of listeners) listener()
}

export function useUpload() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => upload,
  )
}

async function putChunk(
  id: string,
  file: File,
  offset: number,
  chunkBytes: number,
): Promise<ChunkAnswer> {
  const chunk = file.slice(offset, offset + chunkBytes)
  try {
    const { data, error, response } = await auth.client.PUT("/api/v1/me/imports/{id}/archive", {
      params: { path: { id }, query: { offset } },
      body: chunk as unknown as string,
      // openapi-fetch serialises anything but `FormData` as JSON, which sends a `Blob` as `{}`.
      bodySerializer: () => chunk,
      headers: { "Content-Type": "application/octet-stream" },
      signal: controller.signal,
    })
    if (data === undefined) return refusedChunk(response.status, error)
    return { uploadedBytes: data.uploadedBytes }
  } catch {
    // A request that never reached the API: the loop decides whether it is worth another attempt.
    return null
  }
}

/** The upload closed, or the refusal that stops it: a request that never arrived stops it too. */
async function complete(id: string): Promise<UploadStep> {
  try {
    const { error, response } = await auth.client.POST("/api/v1/me/imports/{id}/archive/complete", {
      params: { path: { id } },
    })
    return response.ok ? { next: "COMPLETE" } : { next: "STOP", code: refusalCode(error) }
  } catch {
    return { next: "STOP", code: null }
  }
}

async function send(
  importId: string,
  file: File,
  chunkBytes: number,
  from: number,
  settle: () => void,
) {
  const { signal } = controller
  let offset = from
  let step: UploadStep = { next: "SEND", offset, failures: 0 }
  while (step.next === "SEND") {
    offset = step.offset
    publish({ importId, file, sent: offset, state: "SENDING", code: null })
    if (step.failures > 0) await new Promise((resolve) => setTimeout(resolve, RETRY_MS))
    if (signal.aborted) return
    const answer = await putChunk(importId, file, offset, chunkBytes)
    if (signal.aborted) return
    step = nextStep(answer, offset, file.size, step.failures)
  }
  if (step.next === "PAUSE") {
    resume = () => void send(importId, file, chunkBytes, offset, settle)
    publish({ importId, file, sent: offset, state: "PAUSED", code: null })
    return
  }
  if (step.next === "COMPLETE") step = await complete(importId)
  if (signal.aborted) return
  const stopped = { importId, file, sent: offset, state: "STOPPED" as const }
  publish(step.next === "STOP" ? { ...stopped, code: step.code } : null)
  settle()
}

export function resumeUpload() {
  resume()
}

/** Stops the upload in this tab and leaves the server's import alone. Tests reset with it. */
export function dropUpload() {
  controller.abort()
  controller = new AbortController()
  publish(null)
}

/** A new import, and the upload of its archive one chunk at a time (decision F4). */
export function useStartImport() {
  const queryClient = useQueryClient()
  const settle = () => void queryClient.invalidateQueries({ queryKey: LATEST })
  return useMutation({
    mutationFn: async ({ file, chunkBytes }: { file: File; chunkBytes: number }) => {
      const { data, error, response } = await auth.client.POST("/api/v1/me/imports")
      if (data === undefined) throw new AccountRefusal(error, response.status)
      writeRecord(data.id, file)
      dropUpload()
      void send(data.id, file, chunkBytes, 0, settle)
    },
    onSuccess: settle,
  })
}

/** The same file chosen after a reload: the upload goes on at the row's length (decision D'2). */
export function useResumeImport() {
  const queryClient = useQueryClient()
  const settle = () => void queryClient.invalidateQueries({ queryKey: LATEST })
  return (row: Import, file: File, chunkBytes: number) => {
    dropUpload()
    void send(row.id, file, chunkBytes, row.uploadedBytes, settle)
  }
}

/** What the import already created stays: `DELETE` cancels what is left (section 2). */
export function useCancelImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      dropUpload()
      // `response.ok` and never `data`: a 204 leaves it undefined.
      const { response } = await auth.client.DELETE("/api/v1/me/imports/{id}", {
        params: { path: { id } },
      })
      if (!response.ok) throw new Error(`The API kept the import: ${response.status}.`)
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: LATEST }),
  })
}
