import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

describe("open a pin", () => {
  it("Given a tile, Then the pin opens with what the API knows of it", async () => {
    const boardId = "5e0d2a52-6f7c-4f5e-9c2a-8b3e0d1a7c44"
    const opened = {
      ...readyPin("a harbour at dusk"),
      sourceContextUrl: "https://photos.example.test/harbours/dusk",
      tags: [{ name: "harbours" }],
      boards: [{ id: boardId, name: "Evenings" }],
    }
    server.use(sessionRoute(() => true), pinsRoute([[opened]]), downloadsRoute(), handshakeRoute())
    renderApp("/")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("img", { name: opened.description }))

    const dialog = await screen.findByRole("dialog")
    // The original, under the rendition the grid drew, which a column jsdom measures at 0 px makes the small one.
    const image = within(dialog).getByRole("img", { name: opened.description })
    expect(image).toHaveAttribute("src", `/api/v1/pins/${opened.id}/image`)
    expect(dialog.querySelector('img[alt=""]')).toHaveAttribute(
      "src",
      `/api/v1/pins/${opened.id}/image?size=SMALL`,
    )
    // The page the pin was found on, named by its host and opened beside the application.
    const source = within(dialog).getByRole("link", { name: "photos.example.test" })
    expect(source).toHaveAttribute("href", opened.sourceContextUrl)
    expect(source).toHaveAttribute("target", "_blank")
    expect(within(dialog).getByText("harbours")).toBeVisible()
    expect(within(dialog).getByRole("link", { name: "Evenings" })).toHaveAttribute(
      "href",
      `/boards/${boardId}`,
    )
  })

  it("Given a grid drawn at the medium rendition, Then the pin loads under that one", async () => {
    const opened = readyPin("a harbour at dusk")
    // A small rendition under 0 px is what makes a column jsdom measures at 0 px ask for the medium one.
    server.use(
      sessionRoute(() => true),
      pinsRoute([[opened]]),
      downloadsRoute(),
      handshakeRoute({ small: -1 }),
    )
    renderApp("/")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("img", { name: opened.description }))

    const dialog = await screen.findByRole("dialog")
    expect(dialog.querySelector('img[alt=""]')).toHaveAttribute(
      "src",
      `/api/v1/pins/${opened.id}/image?size=MEDIUM`,
    )
  })

  it("Given an open pin, Then closing it returns to the grid", async () => {
    const opened = readyPin("a harbour at dusk")
    server.use(sessionRoute(() => true), pinsRoute([[opened]]), downloadsRoute(), handshakeRoute())
    renderApp("/")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("img", { name: opened.description }))
    await user.click(await screen.findByRole("button", { name: m.close() }))

    expect(screen.queryByRole("dialog")).toBeNull()
  })
})
