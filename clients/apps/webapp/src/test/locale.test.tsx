import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  board,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  readyPin,
  renderApp,
  sessionRoute,
} from "./app"
import { server } from "./server"

const EVENINGS = board("Evenings")
const HARBOURS = board("Harbours")

/**
 * react-aria reads `navigator.language` once, when its module is evaluated, and republishes it on
 * `languagechange`; the event is the only way a test that already holds the module can move the
 * browser's language, and it only lands once a component of react-aria's is mounted to hear it.
 */
function theBrowserSwitchesTo(language: string) {
  Object.defineProperty(window.navigator, "language", { value: language, configurable: true })
  window.dispatchEvent(new Event("languagechange"))
}

describe("the application's own locale", () => {
  it("Given a browser set to French, Then a control's own words are still the application's", async () => {
    const pin = {
      ...readyPin("a harbour at dusk"),
      boards: [
        { id: EVENINGS.id, name: EVENINGS.name },
        { id: HARBOURS.id, name: HARBOURS.name },
      ],
    }
    server.use(
      sessionRoute(() => true),
      pinsRoute([[pin]]),
      http.get("/api/v1/pins/:pinId", () => HttpResponse.json(pin)),
      ...boardRoutes([EVENINGS, HARBOURS]),
      downloadsRoute(),
      handshakeRoute(),
    )
    renderApp("/")
    const user = userEvent.setup()
    await user.click(await screen.findByRole("img", { name: pin.description }))

    theBrowserSwitchesTo("fr-FR")
    await user.click(await screen.findByRole("button", { name: m.edit_pin() }))

    // The board select joins its chosen values itself, in words nobody here wrote.
    const chosen = within(screen.getByRole("dialog")).getByRole("button", {
      name: new RegExp(m.boards()),
    })
    expect(chosen).toHaveTextContent(`${EVENINGS.name} and ${HARBOURS.name}`)
  })
})
