import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http } from "msw"
import { describe, expect, it } from "vitest"
import {
  board,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  readyPin,
  renderApp,
  searchedPage,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const HARBOURS = board("Harbours", "Where the boats are", 2)
const HELD = readyPin("a cat on a quay")
const ALSO_HELD = readyPin("a harbour at dusk")
const ELSEWHERE = readyPin("a cat asleep")

/** A board of two pins, and a catalogue that holds a third one matching the same term. */
function account(terms: string[] = []) {
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/pins", searchedPage([HELD, ALSO_HELD, ELSEWHERE], () => {})),
    http.get(
      "/api/v1/boards/:boardId/pins",
      searchedPage([HELD, ALSO_HELD], (term) => terms.push(term ?? "none")),
    ),
    downloadsRoute(),
    handshakeRoute(),
    ...boardRoutes([HARBOURS]),
  )
}

function field() {
  return screen.getByRole("searchbox", { name: "Search Harbours" })
}

describe("search inside a board and widen it", () => {
  it("Given a term typed on a board, Then the board alone is searched", async () => {
    const terms: string[] = []
    account(terms)

    const { router } = renderApp(`/boards/${HARBOURS.id}`)
    expect(await screen.findByRole("img", { name: ALSO_HELD.description })).toBeVisible()
    await userEvent.type(field(), "cat")

    // Both at once: the grid is pending between the two pages and holds neither tile then.
    await waitFor(() => {
      expect(screen.getByRole("img", { name: HELD.description })).toBeVisible()
      expect(screen.queryByRole("img", { name: ALSO_HELD.description })).toBeNull()
    })
    // The catalogue's own match is not the board's, the board's route being the one asked.
    expect(screen.queryByRole("img", { name: ELSEWHERE.description })).toBeNull()
    expect(router.state.location.search.q).toBe("cat")
    expect(terms).toEqual(["none", "cat"])
  })

  it("Given a search on a board, Then the link under the field widens it to the catalogue", async () => {
    account()

    const { router } = renderApp(`/boards/${HARBOURS.id}?q=cat`)
    expect(await screen.findByRole("img", { name: HELD.description })).toBeVisible()
    await userEvent.click(screen.getByRole("link", { name: "Search all your pins" }))

    expect(await screen.findByRole("img", { name: ELSEWHERE.description })).toBeVisible()
    // The term is kept and the board is dropped, which is the whole of the widening.
    expect(router.state.location.pathname).toBe("/")
    expect(router.state.location.search.q).toBe("cat")
    expect(screen.getByRole("searchbox", { name: "Search your pins" })).toHaveValue("cat")
  })

  it("Given no search on a board, Then there is nothing to widen", async () => {
    account()

    renderApp(`/boards/${HARBOURS.id}`)

    expect(await screen.findByRole("img", { name: HELD.description })).toBeVisible()
    expect(screen.queryByRole("link", { name: "Search all your pins" })).toBeNull()
  })
})
