import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it } from "vitest"
import { handshakeRoute, onePinPage, renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

/** The button's name is the only thing that says where the user is, so the cycle is read from it. */
const state = () => screen.findByRole("button", { name: /theme$/ })

describe("choosing a theme against the system", () => {
  beforeEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.theme
    server.use(sessionRoute(() => true), onePinPage(() => []), handshakeRoute())
  })

  it("Given a light machine, Then the switch follows it until the cycle overrides it", async () => {
    const user = userEvent.setup()
    renderApp("/")

    expect(await state()).toHaveAccessibleName("System theme")
    expect(document.documentElement.dataset.theme).toBe("light")

    await user.click(await state())
    expect(await state()).toHaveAccessibleName("Light theme")
    expect(document.documentElement.dataset.theme).toBe("light")

    await user.click(await state())
    expect(await state()).toHaveAccessibleName("Dark theme")
    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(localStorage.getItem("pinry-theme")).toBe("dark")

    // The third press returns to the start: a cycle that skipped a state or stopped at the last
    // would have said something else by now.
    await user.click(await state())
    expect(await state()).toHaveAccessibleName("System theme")
    expect(document.documentElement.dataset.theme).toBe("light")
  })

  // The entry point's own pre-paint is not what this reaches: `renderApp` mounts the router, not
  // `main.tsx`. What it holds is that the control is on the credentials screen too, a visitor with
  // no session having no other, and that a stored choice is read back and painted there.
  it("Given no session, Then the credentials screen carries the control and the stored choice", async () => {
    localStorage.setItem("pinry-theme", "dark")

    renderApp("/sign-in")

    expect(await state()).toHaveAccessibleName("Dark theme")
    expect(document.documentElement.dataset.theme).toBe("dark")
  })
})
