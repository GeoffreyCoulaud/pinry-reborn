import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it } from "vitest"
import {
  downloadsRoute,
  handshakeRoute,
  readyPin,
  renderApp,
  sessionRoute,
  sortedPinsRoute,
} from "../test/app"
import { server } from "../test/server"

const OLDEST = readyPin("a harbour at dusk")
const NEWEST = readyPin("a cat asleep")

/** The catalogue as the API orders it, oldest first, which is what the route sorts. */
function catalogue(sorts: string[]) {
  server.use(
    sessionRoute(() => true),
    sortedPinsRoute([OLDEST, NEWEST], (sort) => sorts.push(sort ?? "none")),
    downloadsRoute(),
    handshakeRoute(),
  )
}

/** The tiles in the order the grid laid them out, read by the description each one carries. */
function tileOrder() {
  return screen.getAllByRole("img").map((tile) => tile.getAttribute("alt"))
}

describe("choosing the grid's order", () => {
  it("Given an address with no order, Then the grid asks for the newest pins first", async () => {
    const sorts: string[] = []
    catalogue(sorts)

    renderApp("/")

    expect(await screen.findByRole("img", { name: NEWEST.description })).toBeVisible()
    // The API's own default is oldest first, which buries a pin just created (specification 2.6).
    expect(sorts).toEqual(["CREATED_AT_DESC"])
    expect(tileOrder()).toEqual([NEWEST.description, OLDEST.description])
  })

  it("Given the user picks the oldest first, Then the request and the tiles both follow", async () => {
    const sorts: string[] = []
    catalogue(sorts)

    const { router } = renderApp("/")
    expect(await screen.findByRole("img", { name: NEWEST.description })).toBeVisible()
    // react-aria names the trigger with the chosen order first and the control's own label after.
    await userEvent.click(screen.getByRole("button", { name: "Newest first Order" }))
    await userEvent.click(await screen.findByRole("option", { name: "Oldest first" }))

    await waitFor(() => expect(tileOrder()).toEqual([OLDEST.description, NEWEST.description]))
    // Two orders sharing one query key make the grid serve one order's pages under the other.
    expect(sorts).toEqual(["CREATED_AT_DESC", "CREATED_AT_ASC"])
    expect(router.state.location.searchStr).toBe("?sort=CREATED_AT_ASC")
  })

  it("Given an address carrying an order, Then a reload of it opens on that order", async () => {
    const sorts: string[] = []
    catalogue(sorts)

    renderApp("/?sort=CREATED_AT_ASC")

    expect(await screen.findByRole("img", { name: OLDEST.description })).toBeVisible()
    expect(sorts).toEqual(["CREATED_AT_ASC"])
    expect(tileOrder()).toEqual([OLDEST.description, NEWEST.description])
  })

  it("Given an address carrying an order the API would refuse, Then the default stands in", async () => {
    const sorts: string[] = []
    catalogue(sorts)

    renderApp("/?sort=OLDEST_ON_A_TUESDAY")

    expect(await screen.findByRole("img", { name: NEWEST.description })).toBeVisible()
    expect(sorts).toEqual(["CREATED_AT_DESC"])
  })

  it("Given the credentials screen, Then it carries no order to choose", async () => {
    server.use(sessionRoute(() => false))

    renderApp("/sign-in")

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeVisible()
    // The bar is the same component on both screens, and its children are the screen's own.
    expect(screen.queryByRole("button", { name: /Order/ })).toBeNull()
  })
})
