import { screen } from "@testing-library/react"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import { downloadsRoute, handshakeRoute, pinsRoute, renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

const EMPTY_PAGE = { pins: [], pagination: { previousCursor: null, nextCursor: null } }

describe("an account with no pins", () => {
  it("Given an account with nothing in it, Then the grid says so rather than standing empty", async () => {
    server.use(sessionRoute(() => true), pinsRoute([]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByText(m.pins_empty())).toBeVisible()
  })

  it("Given the first page is still on its way, Then the grid says it is loading", async () => {
    // The page is held rather than delayed: a duration races the assertion, and React reuses the
    // very node the status role is read from, so a late read finds the empty state in it.
    let release = () => {}
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/pins", async () => {
        await held
        return HttpResponse.json(EMPTY_PAGE)
      }),
      downloadsRoute(),
      handshakeRoute(),
    )

    renderApp("/")

    expect(await screen.findByRole("status")).toHaveTextContent(m.pins_loading())
    release()
    expect(await screen.findByText(m.pins_empty())).toBeVisible()
  })
})
