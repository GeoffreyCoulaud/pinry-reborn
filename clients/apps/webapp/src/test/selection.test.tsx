import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import type { Pin } from "../pins"
import {
  board,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "./app"
import { server } from "./server"

const HARBOURS = board("Harbours")
const FIRST = readyPin("a harbour at dusk")
const SECOND = readyPin("a harbour at dawn")

/** Tick the rows or the tiles the case acts on: react-aria names a tick after the row it sits in. */
async function select(user: ReturnType<typeof userEvent.setup>, ...names: string[]) {
  for (const name of names) {
    await user.click(await screen.findByRole("checkbox", { name: new RegExp(name) }))
  }
}

/** The three gestures of the pin grid are two here: a board's grid is the only place this one is. */
describe("a selection on a board's own grid", () => {
  it("Given two pins removed from the board, Then one request leaves and no page is refetched", async () => {
    const removed: unknown[] = []
    let pages = 0
    server.use(
      sessionRoute(() => true),
      onePinPage(() => []),
      // Counted here rather than through `boardPinsRoute`, which reports the sort alone.
      http.get("/api/v1/boards/:boardId/pins", () => {
        pages += 1
        return HttpResponse.json({
          pins: [FIRST, SECOND],
          pagination: { previousCursor: null, nextCursor: null },
        })
      }),
      http.delete("/api/v1/boards/:boardId/pins", async ({ request, params }) => {
        removed.push({ boardId: String(params.boardId), ...((await request.json()) as object) })
        return new HttpResponse(null, { status: 204 })
      }),
      downloadsRoute(),
      handshakeRoute(),
      ...boardRoutes([HARBOURS]),
    )
    renderApp(`/boards/${HARBOURS.id}`)
    const user = userEvent.setup()

    await select(user, FIRST.description, SECOND.description)
    await user.click(screen.getByRole("button", { name: m.remove_from_board() }))

    expect(removed).toEqual([{ boardId: HARBOURS.id, pinIds: [FIRST.id, SECOND.id] }])
    // The tiles leave the cached pages rather than the board being read again (decision P).
    expect(await screen.findByRole("status")).toHaveTextContent(m.pins_empty())
    expect(pages).toBe(1)
  })
})

/** Both tabs of the bin carry a selection, and Restore alone (decision F). */
describe("a selection in the recycle bin", () => {
  function bin(pins: Pin[], restored: { pins: unknown[]; boards: unknown[] }) {
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/pins/recycled", () =>
        HttpResponse.json({ pins, pagination: { previousCursor: null, nextCursor: null } }),
      ),
      http.get("/api/v1/boards/recycled", () => HttpResponse.json({ boards: [HARBOURS] })),
      http.post("/api/v1/pins/recycled/restore", async ({ request }) => {
        restored.pins.push(await request.json())
        return new HttpResponse(null, { status: 204 })
      }),
      http.post("/api/v1/boards/recycled/restore", async ({ request }) => {
        restored.boards.push(await request.json())
        return new HttpResponse(null, { status: 204 })
      }),
      onePinPage(() => []),
      downloadsRoute(),
      handshakeRoute(),
      ...boardRoutes([]),
    )
  }

  it("Given two pins selected in the bin, Then one request restores both", async () => {
    const restored = { pins: [] as unknown[], boards: [] as unknown[] }
    bin([FIRST, SECOND], restored)
    renderApp("/recycled")
    const user = userEvent.setup()

    await select(user, FIRST.description, SECOND.description)
    expect(screen.getByText(m.selected_count({ count: 2 }))).toBeVisible()
    await user.click(screen.getByRole("button", { name: m.restore_selection() }))

    expect(restored.pins).toEqual([{ pinIds: [FIRST.id, SECOND.id] }])
  })

  it("Given a board selected on the other tab, Then that tab's own bar restores it", async () => {
    const restored = { pins: [] as unknown[], boards: [] as unknown[] }
    bin([], restored)
    renderApp("/recycled")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("tab", { name: m.boards() }))
    await select(user, HARBOURS.name)
    await user.click(screen.getByRole("button", { name: m.restore_selection() }))

    expect(restored.boards).toEqual([{ boardIds: [HARBOURS.id] }])
    expect(restored.pins).toEqual([])
  })
})
