import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useSyncExternalStore } from "react"
import { auth, bodyOf } from "./api"
import { POLL_MS } from "./lib/downloads"
import {
  RETRY_MS,
  nextStep,
  refusedChunk,
  type Attempt,
  type ChunkAnswer,
  type FileIdentity,
  type UploadStep,
} from "./lib/imports"
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

// One record per import, under its id, so a reload can ask for the same file.
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

function forgetRecord(id: string) {
  try {
    localStorage.removeItem(RECORD + id)
  } catch {
    // Nothing could be written there either.
  }
}

/**
 * The latest import's, while it waits on its archive, and this tab's upload's, which a read begun
 * before its import opened does not know of: no record outlives what it serves.
 */
function pruneRecords(latest: Import | null) {
  const kept = [latest?.state === "AWAITING_ARCHIVE" ? latest.id : null, upload?.importId]
  try {
    for (const key of Object.keys(localStorage)) {
      if (key.startsWith(RECORD) && !kept.some((id) => key === RECORD + id)) {
        localStorage.removeItem(key)
      }
    }
  } catch {
    // Nothing could be written there either.
  }
}

/** The newest import or none, as the export's section reads its own. */
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

/** One import's report, a page at a time on the cursor each page answers. */
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

// The store lives above the router, so changing screen leaves the upload running.
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

/** What a request of the upload came back with, or `null` for one that never reached the API. */
async function answerOf(request: Promise<{ data?: Import; error?: unknown; response: Response }>) {
  try {
    const { data, error, response } = await request
    if (data === undefined) return refusedChunk(response.status, error)
    return { uploadedBytes: data.uploadedBytes }
  } catch {
    return null
  }
}

function putChunk(id: string, file: File, offset: number, chunkBytes: number) {
  const chunk = file.slice(offset, offset + chunkBytes)
  return answerOf(
    auth.client.PUT("/api/v1/me/imports/{id}/archive", {
      params: { path: { id }, query: { offset } },
      body: chunk as unknown as string,
      // openapi-fetch serialises anything but `FormData` as JSON, which sends a `Blob` as `{}`.
      bodySerializer: () => chunk,
      headers: { "Content-Type": "application/octet-stream" },
      signal: controller.signal,
    }),
  )
}

async function send(
  importId: string,
  file: File,
  chunkBytes: number,
  from: Attempt,
  settle: () => Promise<unknown>,
) {
  const { signal } = controller
  let attempt = from
  let step: UploadStep = from
  let sent = 0
  while (step.next === "SEND" || step.next === "COMPLETE") {
    attempt = step
    sent = attempt.next === "SEND" ? attempt.offset : file.size
    publish({ importId, file, sent, state: "SENDING", code: null })
    if (attempt.failures > 0) await new Promise((resolve) => setTimeout(resolve, RETRY_MS))
    if (signal.aborted) return
    const answer: ChunkAnswer =
      attempt.next === "SEND"
        ? await putChunk(importId, file, attempt.offset, chunkBytes)
        : await answerOf(
            auth.client.POST("/api/v1/me/imports/{id}/archive/complete", {
              params: { path: { id: importId } },
            }),
          )
    if (signal.aborted) return
    step = nextStep(answer, attempt, file.size)
  }
  if (step.next === "PAUSE") {
    const paused = { ...attempt, failures: 0 }
    resume = () => void send(importId, file, chunkBytes, paused, settle)
    publish({ importId, file, sent, state: "PAUSED", code: null })
    return
  }
  if (step.next === "STOP") {
    publish({ importId, file, sent, state: "STOPPED", code: step.code })
    void settle()
    return
  }
  // The server's row takes over once read, so the stale one never shows in between.
  forgetRecord(importId)
  await settle()
  if (!signal.aborted) publish(null)
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

/** A new import, and the upload of its archive one chunk at a time. */
export function useStartImport() {
  const queryClient = useQueryClient()
  const settle = () => queryClient.invalidateQueries({ queryKey: LATEST })
  return useMutation({
    mutationFn: async ({ file, chunkBytes }: { file: File; chunkBytes: number }) => {
      const { data, error, response } = await auth.client.POST("/api/v1/me/imports")
      if (data === undefined) throw new AccountRefusal(error, response.status)
      writeRecord(data.id, file)
      dropUpload()
      void send(data.id, file, chunkBytes, { next: "SEND", offset: 0, failures: 0 }, settle)
    },
    onSuccess: settle,
  })
}

/** The same file chosen after a reload: the upload goes on at the row's length. */
export function useResumeImport() {
  const queryClient = useQueryClient()
  const settle = () => queryClient.invalidateQueries({ queryKey: LATEST })
  return (row: Import, file: File, chunkBytes: number) => {
    dropUpload()
    const from = { next: "SEND", offset: row.uploadedBytes, failures: 0 } as const
    void send(row.id, file, chunkBytes, from, settle)
  }
}

/** What the import already created stays: `DELETE` cancels what is left. */
export function useCancelImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      // `response.ok` and never `data`: a 204 leaves it undefined.
      const { response } = await auth.client.DELETE("/api/v1/me/imports/{id}", {
        params: { path: { id } },
      })
      if (!response.ok) throw new Error(`The API kept the import: ${response.status}.`)
    },
    // The upload runs on until the row that replaces it is read, so a refused cancel leaves it be.
    onSettled: async (_, error) => {
      await queryClient.invalidateQueries({ queryKey: LATEST })
      if (error === null) dropUpload()
    },
  })
}
