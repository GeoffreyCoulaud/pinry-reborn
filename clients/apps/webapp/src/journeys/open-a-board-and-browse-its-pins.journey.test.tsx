import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { delay, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  board,
  boardPinsRoute,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const HARBOURS = board("Harbours", "Where the boats are", 2)
const MOUNTAINS = board("Mountains", "Up high", 1)
const FIRST = readyPin("a harbour at dusk")
const SECOND = readyPin("a cat asleep")
const ELSEWHERE = readyPin("a ridge at noon")

/** Two boards, one of them paged over two pages, and a catalogue holding neither's pins. */
function account(onSort: (sort: string | null) => void = () => {}) {
  server.use(
    sessionRoute(() => true),
    onePinPage(() => [ELSEWHERE]),
    boardPinsRoute({ [HARBOURS.id]: [[FIRST], [SECOND]], [MOUNTAINS.id]: [[ELSEWHERE]] }, onSort),
    downloadsRoute(),
    handshakeRoute(),
    ...boardRoutes([HARBOURS, MOUNTAINS]),
  )
}

describe("open a board and browse its pins", () => {
  it("Given the boards screen, Then a board opens on its own pins and loads a second page", async () => {
    account()

    renderApp("/boards")
    await userEvent.click(await screen.findByRole("link", { name: "Harbours" }))

    expect(await screen.findByRole("heading", { name: "Harbours" })).toBeVisible()
    expect(screen.getByText("Where the boats are")).toBeVisible()
    expect(await screen.findByRole("img", { name: FIRST.description })).toBeVisible()
    // The sentinel reaches the end of the first page and the second follows it.
    expect(await screen.findByRole("img", { name: SECOND.description })).toBeVisible()
    // A pin of the catalogue is not a pin of this board, and a drop files one on the home screen
    // alone, so the screen that creates pins is not this one.
    expect(screen.queryByRole("img", { name: ELSEWHERE.description })).toBeNull()
    expect(screen.queryByRole("button", { name: "Add a pin" })).toBeNull()
  })

  it("Given a board's own address, Then the order it carries is the one its grid asks for", async () => {
    const sorts: string[] = []
    account((sort) => sorts.push(sort ?? "none"))

    const { unmount } = renderApp(`/boards/${HARBOURS.id}`)
    expect(await screen.findByRole("img", { name: FIRST.description })).toBeVisible()
    expect(sorts[0]).toBe("CREATED_AT_DESC")

    unmount()
    sorts.length = 0
    renderApp(`/boards/${HARBOURS.id}?sort=CREATED_AT_ASC`)
    expect(await screen.findByRole("img", { name: FIRST.description })).toBeVisible()
    expect(sorts[0]).toBe("CREATED_AT_ASC")
  })

  it("Given a board opened, Then the home grid is not served the board's pages", async () => {
    account()
    // The catalogue never answers, so what the home grid shows is what its own key already holds.
    server.use(http.get("/api/v1/pins", () => delay("infinite")))

    renderApp(`/boards/${HARBOURS.id}`)
    expect(await screen.findByRole("img", { name: FIRST.description })).toBeVisible()
    await userEvent.click(screen.getByRole("link", { name: "Your pins" }))

    // One key for two catalogues hands the home grid the board's pages under it.
    expect(await screen.findByText("Loading your pins.")).toBeVisible()
    expect(screen.queryByRole("img", { name: FIRST.description })).toBeNull()
  })

  it("Given an address naming a board the account does not hold, Then the screen says so", async () => {
    account()

    renderApp("/boards/0f5c6e58-2d6c-4a3a-9c1f-000000000000")

    expect(await screen.findByRole("alert")).toHaveTextContent("That board could not be found.")
  })
})
