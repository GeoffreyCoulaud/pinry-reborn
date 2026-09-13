import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it } from "vitest"
import { handshakeRoute, onePinPage, renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

describe("choosing a theme against the system", () => {
  beforeEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.theme
    server.use(sessionRoute(() => true), onePinPage(() => []), handshakeRoute())
  })

  it("Given a light machine, Then the switch follows it until a choice overrides it", async () => {
    const user = userEvent.setup()
    renderApp("/")

    const switcher = await screen.findByLabelText("Theme")
    expect(switcher).toHaveValue("system")
    expect(document.documentElement.dataset.theme).toBe("light")

    await user.selectOptions(switcher, "Dark")

    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(localStorage.getItem("pinry-theme")).toBe("dark")
  })

  // The entry point's own pre-paint is not what this reaches: `renderApp` mounts the router, not
  // `main.tsx`. What it holds is that a stored choice is read back and painted, on either screen.
  it("Given a stored choice, Then it is what the next visit paints, on either screen", async () => {
    localStorage.setItem("pinry-theme", "dark")

    renderApp("/pins/new")

    expect(await screen.findByLabelText("Theme")).toHaveValue("dark")
    expect(document.documentElement.dataset.theme).toBe("dark")
  })
})
