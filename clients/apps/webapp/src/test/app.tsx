import type { Schemas } from "@pinry-reborn/auth"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RouterProvider, createMemoryHistory } from "@tanstack/react-router"
import { render } from "@testing-library/react"
import { HttpResponse, http } from "msw"
import { createAppRouter } from "../router"

type Pin = Schemas["PinOutputDto"]

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
 * The catalogue the grid pages through. A cursor is the index of the page it answers, which is
 * all the client may assume of it: block 5 made it an opaque string.
 */
export function pinsRoute(pages: Pin[][], onRequest: () => void = () => {}) {
  return http.get("/api/v1/pins", ({ request }) => {
    onRequest()
    const cursor = new URL(request.url).searchParams.get("cursor")
    const index = cursor === null ? 0 : Number(cursor)
    return HttpResponse.json({
      pins: pages[index] ?? [],
      pagination: {
        previousCursor: index > 0 ? String(index - 1) : null,
        nextCursor: index + 1 < pages.length ? String(index + 1) : null,
      },
    })
  })
}

/** The catalogue as a single page, reread each time the journey's own state changes it. */
export function onePinPage(pins: () => Pin[]) {
  return http.get("/api/v1/pins", () =>
    HttpResponse.json({ pins: pins(), pagination: { previousCursor: null, nextCursor: null } }),
  )
}

/** The task centre's list, answered from what the journey decided last. */
export function downloadsRoute(rows: () => unknown[] = () => []) {
  return http.get("/api/v1/me/image-downloads", () => HttpResponse.json(downloadsPage(rows())))
}

/** One page of downloads, the shape the route answers: the rows, and no page after them. */
export function downloadsPage(downloads: unknown[], nextCursor?: unknown) {
  return { downloads, pagination: { previousCursor: null, nextCursor: nextCursor ?? null } }
}

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
      limits: { maxFileBytes, maxPixels: 50_000_000 },
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

/** The application on one route, with a cache of its own so no journey inherits another's. */
export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={createAppRouter(createMemoryHistory({ initialEntries: [path] }))} />
    </QueryClientProvider>,
  )
}
