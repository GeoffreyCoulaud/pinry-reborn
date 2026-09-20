import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  board,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const HARBOURS = board("Harbours", "Where the boats are")
const EVENINGS = board("Evenings")
const FIRST = readyPin("a harbour at dusk")
const SECOND = readyPin("a harbour at dawn")
const THIRD = readyPin("a cat asleep")

/** The catalogue, the boards, and the one route that files pins under one of them. */
function account(filed: unknown[], refusal?: number) {
  server.use(
    sessionRoute(() => true),
    onePinPage(() => [FIRST, SECOND, THIRD]),
    http.post("/api/v1/boards/:boardId/pins", async ({ request, params }) => {
      if (refusal !== undefined) return new HttpResponse(null, { status: refusal })
      filed.push({ boardId: String(params.boardId), ...((await request.json()) as object) })
      return new HttpResponse(null, { status: 204 })
    }),
    downloadsRoute(),
    handshakeRoute(),
    ...boardRoutes([HARBOURS, EVENINGS]),
  )
}

/**
 * The gesture the bar exists for: tick two tiles, then say where they go. A tile's tick is named
 * after the tile, react-aria labelling a selection checkbox from the row it sits in.
 */
async function select(user: ReturnType<typeof userEvent.setup>, ...descriptions: string[]) {
  for (const name of descriptions) {
    await user.click(await screen.findByRole("checkbox", { name: new RegExp(name) }))
  }
}

describe("add several selected pins to a board", () => {
  it("Given two pins selected, Then one request files both under the board", async () => {
    const filed: unknown[] = []
    account(filed)
    renderApp("/")
    const user = userEvent.setup()

    await select(user, FIRST.description, SECOND.description)
    expect(screen.getByText(m.selected_count({ count: 2 }))).toBeVisible()

    await user.click(screen.getByRole("button", { name: m.add_to_board() }))
    await user.click(await screen.findByRole("menuitem", { name: HARBOURS.name }))

    // One request and not one per pin: all or nothing is what the route serves (ADR 0039).
    expect(filed).toEqual([{ boardId: HARBOURS.id, pinIds: [FIRST.id, SECOND.id] }])
    // The selection is spent, so the bar goes with it.
    expect(await screen.findByRole("img", { name: FIRST.description })).toBeVisible()
    expect(screen.queryByText(m.selected_count({ count: 2 }))).toBeNull()
  })

  it("Given the API refuses the batch, Then nothing is filed and the refusal is said", async () => {
    account([], 404)
    renderApp("/")
    const user = userEvent.setup()

    await select(user, FIRST.description)
    await user.click(screen.getByRole("button", { name: m.add_to_board() }))
    await user.click(await screen.findByRole("menuitem", { name: EVENINGS.name }))

    expect(await screen.findByText(m.membership_refused())).toBeVisible()
    // The selection stands: the gesture failed, so it is the one to try again.
    expect(screen.getByText(m.selected_count({ count: 1 }))).toBeVisible()
  })

  it("Given a selection the user gives up on, Then the bar's own exit clears it", async () => {
    account([])
    renderApp("/")
    const user = userEvent.setup()

    await select(user, FIRST.description)
    await user.click(screen.getByRole("button", { name: m.clear_selection() }))

    expect(screen.queryByText(m.selected_count({ count: 1 }))).toBeNull()
    expect(screen.getByRole("checkbox", { name: new RegExp(FIRST.description) })).not.toBeChecked()
  })
})
