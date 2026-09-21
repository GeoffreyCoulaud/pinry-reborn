import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http } from "msw"
import { describe, expect, it } from "vitest"
import {
  downloadsRoute,
  handshakeRoute,
  readyPin,
  renderApp,
  searchedPage,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const CAT = readyPin("a cat asleep")
const HARBOUR = readyPin("a harbour at dusk")

/** A catalogue of two pins, of which one matches `cat`, and every term the grid asked for. */
function account(terms: string[]) {
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/pins", searchedPage([CAT, HARBOUR], (term) => terms.push(term ?? "none"))),
    downloadsRoute(),
    handshakeRoute(),
  )
}

function field() {
  return screen.getByRole("searchbox", { name: "Search your pins" })
}

describe("search from the header", () => {
  it("Given a term typed in the header, Then the address carries it and the grid holds the matches", async () => {
    const terms: string[] = []
    account(terms)

    const { router } = renderApp("/")
    expect(await screen.findByRole("img", { name: HARBOUR.description })).toBeVisible()
    await userEvent.type(field(), "cat")

    expect(await screen.findByRole("heading", { name: "Search results" })).toBeVisible()
    // Both at once: the grid is pending between the two pages and holds neither tile then.
    await waitFor(() => {
      expect(screen.getByRole("img", { name: CAT.description })).toBeVisible()
      expect(screen.queryByRole("img", { name: HARBOUR.description })).toBeNull()
    })
    expect(router.state.location.search.q).toBe("cat")
    // The pause is the whole of decision P: three characters are one search, not three.
    expect(terms).toEqual(["none", "cat"])
  })

  it("Given an address carrying a term, Then the field opens on it and the grid is already filtered", async () => {
    const terms: string[] = []
    account(terms)

    renderApp("/?q=cat")

    expect(await screen.findByRole("img", { name: CAT.description })).toBeVisible()
    expect(screen.queryByRole("img", { name: HARBOUR.description })).toBeNull()
    expect(field()).toHaveValue("cat")
    expect(terms).toEqual(["cat"])
  })

  it("Given the field emptied, Then the whole catalogue comes back", async () => {
    const terms: string[] = []
    account(terms)

    const { router } = renderApp("/?q=cat")
    expect(await screen.findByRole("img", { name: CAT.description })).toBeVisible()
    await userEvent.clear(field())

    expect(await screen.findByRole("img", { name: HARBOUR.description })).toBeVisible()
    // An empty field is not a search for emptiness: the parameter leaves the address.
    expect(router.state.location.search.q).toBeUndefined()
    expect(terms).toEqual(["cat", "none"])
  })

  it("Given a term nothing matches, Then the grid says the search found nothing", async () => {
    const terms: string[] = []
    account(terms)

    renderApp("/?q=zebra")

    // Not `pins_empty`, which states an empty account and would send the user to add a pin.
    expect(await screen.findByText("Nothing matches zebra.")).toBeVisible()
  })
})
