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

    // The switch is a trigger and a listbox rather than a native select, so what it holds is read
    // from its accessible name: the chosen option, then the label.
    const switcher = await screen.findByLabelText("Theme")
    expect(switcher).toHaveAccessibleName("System Theme")
    expect(document.documentElement.dataset.theme).toBe("light")

    await user.click(switcher)
    await user.click(await screen.findByRole("option", { name: "Dark" }))

    expect(document.documentElement.dataset.theme).toBe("dark")
    expect(localStorage.getItem("pinry-theme")).toBe("dark")
  })

  // The entry point's own pre-paint is not what this reaches: `renderApp` mounts the router, not
  // `main.tsx`. What it holds is that a stored choice is read back and painted, on either screen.
  it("Given a stored choice, Then it is what the next visit paints, on either screen", async () => {
    localStorage.setItem("pinry-theme", "dark")

    renderApp("/pins/new")

    expect(await screen.findByLabelText("Theme")).toHaveAccessibleName("Dark Theme")
    expect(document.documentElement.dataset.theme).toBe("dark")
  })
})
