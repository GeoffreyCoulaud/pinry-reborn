import { Toast } from "@heroui/react"
import type { Schemas } from "@pinry-reborn/auth"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RouterProvider, createMemoryHistory } from "@tanstack/react-router"
import { render } from "@testing-library/react"
import { HttpResponse, http } from "msw"
import { I18nProvider } from "react-aria-components"
import { getLocale } from "../paraglide/runtime.js"
import { createAppRouter } from "../router"

type Pin = Schemas["PinOutputDto"]
type Board = Schemas["BoardOutputDto"]
type BoardInput = Schemas["BoardInputDto"]

const iso = (offsetMs: number) => new Date(Date.now() + offsetMs).toISOString()

/** Dated from the run: a fixed `renewAfter` falls into the past and renews on every journey. */
export const SESSION = {
  expiresAt: iso(3_600_000),
  renewAfter: iso(1_800_000),
  persistent: false,
}

/** The same session, past the point the API recommends renewing it. */
export const DUE_SESSION = { ...SESSION, renewAfter: iso(-60_000) }

/** The route the application reads its session from, answered from what the journey decided last. */
export function sessionRoute(isOpen: () => boolean, session: typeof SESSION = SESSION) {
  return http.get("/api/v1/sessions/current", () =>
    isOpen() ? HttpResponse.json(session) : new HttpResponse(null, { status: 401 }),
  )
}

/** The renewal, answering a session no longer due and counting what asked for it. */
export function renewRoute(onRequest: () => void = () => {}) {
  return http.post("/api/v1/sessions/current/renew", () => {
    onRequest()
    return HttpResponse.json(SESSION)
  })
}

const AUTHOR_ID = "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11"
let pinCount = 0

/** A pin the journey names by its description, which the tile reads as the image's text. */
export function pin(description: string, image: Pin["image"] = null): Pin {
  const id = `${AUTHOR_ID.slice(0, -2)}${(pinCount++).toString().padStart(2, "0")}`
  return {
    id,
    authorId: AUTHOR_ID,
    sourceContextUrl: `https://example.test/${id}`,
    sourceMediaUrl: null,
    description,
    tags: [],
    boards: [],
    image,
  }
}

/** A pin whose image the API downloaded, at the dimensions the tile is placed with. */
export function readyPin(description: string, width = 800, height = 600): Pin {
  const bare = pin(description)
  const url = `/api/v1/pins/${bare.id}/image`
  return { ...bare, image: { status: "READY", url, width, height } }
}

/**
 * The page a request's cursor names. A cursor is the index of the page it answers, which is all
 * the client may assume of it: block 5 made it an opaque string.
 */
function pageAt(pages: Pin[][], request: Request) {
  const index = Number(new URL(request.url).searchParams.get("cursor") ?? 0)
  return HttpResponse.json({
    pins: pages[index] ?? [],
    pagination: {
      previousCursor: index > 0 ? String(index - 1) : null,
      nextCursor: index + 1 < pages.length ? String(index + 1) : null,
    },
  })
}

/** The catalogue the grid pages through. */
export function pinsRoute(pages: Pin[][], onRequest: () => void = () => {}) {
  return http.get("/api/v1/pins", ({ request }) => {
    onRequest()
    return pageAt(pages, request)
  })
}

/** One board's own catalogue, paged the way `GET /api/v1/pins` is and answering that board alone. */
export function boardPinsRoute(
  pages: Record<string, Pin[][]>,
  onSort: (sort: string | null) => void = () => {},
) {
  return http.get("/api/v1/boards/:boardId/pins", ({ request, params }) => {
    onSort(new URL(request.url).searchParams.get("sort"))
    return pageAt(pages[String(params.boardId)] ?? [], request)
  })
}

/** One page, ordered by the `sort` the grid asked for, which the journey also reads. */
export function sortedPinsRoute(oldestFirst: Pin[], onSort: (sort: string | null) => void) {
  return http.get("/api/v1/pins", ({ request }) => {
    const sort = new URL(request.url).searchParams.get("sort")
    onSort(sort)
    const pins = sort === "CREATED_AT_ASC" ? oldestFirst : [...oldestFirst].reverse()
    return HttpResponse.json({ pins, pagination: { previousCursor: null, nextCursor: null } })
  })
}

/**
 * One page of whichever pins match the `q` the grid sent, as the API matches them: a substring of
 * the description. Every term is reported, the absent one included, so a journey reads how many
 * requests a typed term cost.
 */
export function searchedPage(pins: Pin[], onTerm: (term: string | null) => void) {
  return ({ request }: { request: Request }) => {
    const term = new URL(request.url).searchParams.get("q")
    onTerm(term)
    const held = term === null ? pins : pins.filter((one) => one.description.includes(term))
    return HttpResponse.json({ pins: held, pagination: { previousCursor: null, nextCursor: null } })
  }
}

/** The catalogue as a single page, reread each time the journey's own state changes it. */
export function onePinPage(pins: () => Pin[]) {
  return http.get("/api/v1/pins", () =>
    HttpResponse.json({ pins: pins(), pagination: { previousCursor: null, nextCursor: null } }),
  )
}

let boardCount = 0

/** A board the journey names, as `GET /api/v1/boards` answers one. */
export function board(name: string, description = "", pinCount = 0): Board {
  return { id: `${AUTHOR_ID.slice(0, -4)}b${boardCount++}`, name, description, pinCount }
}

/**
 * The account's boards, served and written by the four routes the screen calls. The array is the
 * journey's own, so what a write leaves behind is what the next read answers.
 */
export function boardRoutes(boards: Board[]) {
  const at = (boardId: unknown) => boards.findIndex((one) => one.id === boardId)
  return [
    http.get("/api/v1/boards", () => HttpResponse.json({ boards })),
    http.post("/api/v1/boards", async ({ request }) => {
      const created = { ...board(""), ...((await request.json()) as BoardInput) }
      boards.push(created)
      return HttpResponse.json(created, { status: 201 })
    }),
    http.put("/api/v1/boards/:boardId", async ({ request, params }) => {
      const index = at(params.boardId)
      const held = boards[index]
      if (held === undefined) return new HttpResponse(null, { status: 404 })
      const saved = { ...held, ...((await request.json()) as BoardInput) }
      boards[index] = saved
      return HttpResponse.json(saved)
    }),
    http.delete("/api/v1/boards/:boardId", ({ params }) => {
      const index = at(params.boardId)
      if (index < 0) return new HttpResponse(null, { status: 404 })
      boards.splice(index, 1)
      return new HttpResponse(null, { status: 204 })
    }),
  ]
}

/** The task centre's list, answered from what the journey decided last. */
export function downloadsRoute(rows: () => unknown[] = () => []) {
  return http.get("/api/v1/me/image-downloads", () => HttpResponse.json(downloadsPage(rows())))
}

/** One page of downloads, the shape the route answers: the rows, and no page after them. */
export function downloadsPage(downloads: unknown[], nextCursor?: unknown) {
  return { downloads, pagination: { previousCursor: null, nextCursor: nextCursor ?? null } }
}

/** The account's exports, newest first, answered from what the journey decided last. */
export function exportsRoute(rows: () => unknown[] = () => []) {
  return http.get("/api/v1/me/exports", () =>
    HttpResponse.json({ exports: rows(), pagination: { previousCursor: null, nextCursor: null } }),
  )
}

/** The account's imports, newest first, answered from what the journey decided last. */
export function importsRoute(rows: () => unknown[] = () => []) {
  return http.get("/api/v1/me/imports", () =>
    HttpResponse.json({ imports: rows(), pagination: { previousCursor: null, nextCursor: null } }),
  )
}

/** What the API's `ImageFormat` holds, which is what a real handshake publishes. */
export const MEDIA_TYPES = ["image/png", "image/jpeg", "image/webp", "image/gif"]

/** The deployment's limits and rendition sizes, as narrow as the journey needs them to be. */
export function handshakeRoute({
  maxFileBytes = 30 * 1024 * 1024,
  small = 240,
  onRequest = () => {},
}: { maxFileBytes?: number; small?: number; onRequest?: () => void } = {}) {
  return http.get("/api/v1/handshake", () => {
    onRequest()
    return HttpResponse.json({
      contractVersion: "4.0.0",
      limits: { maxFileBytes, maxPixels: 50_000_000, mediaTypes: MEDIA_TYPES },
      renditionSizes: { tiny: 80, small, medium: 640, large: 1600 },
    })
  })
}

/** A row of the task centre, as `GET /api/v1/me/image-downloads` answers it. */
export function download(pinId: string, status: "PENDING" | "FAILED", message: string | null = null) {
  return {
    pinId,
    sourceUrl: `https://example.test/${pinId}.png`,
    status,
    requestedAt: "2026-09-11T10:00:00Z",
    updatedAt: "2026-09-11T10:00:01Z",
    reasonCode: status === "FAILED" ? "FETCH_FAILED" : null,
    message,
  }
}

/** A drop as the browser hands one over. `getData` is read on every drop, so it is not optional. */
export function dropOf(files: File[], uriList = "") {
  return { dataTransfer: { files, getData: () => uriList } }
}

/** The application on one route, with a cache of its own so no journey inherits another's. */
export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createAppRouter(createMemoryHistory({ initialEntries: [path] }))
  const rendered = render(
    // As `main.tsx` wraps the tree, so a journey reads the words the application really shows.
    <I18nProvider locale={getLocale()}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        {/* As `main.tsx` mounts it, so a journey reads the toasts the application really shows. */}
        <Toast.Provider />
      </QueryClientProvider>
    </I18nProvider>,
  )
  // The router comes back so a journey can read the address a control wrote.
  return Object.assign(rendered, { router })
}
