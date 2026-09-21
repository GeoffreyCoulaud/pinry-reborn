import { screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import { downloadsRoute, handshakeRoute, pinsRoute, renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

describe("open the application", () => {
  it("Given an open session, Then the application's name is the heading and the way home", async () => {
    server.use(sessionRoute(() => true), pinsRoute([]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByRole("heading", { name: m.app_name() })).toBeVisible()
    expect(screen.getByRole("link", { name: m.app_name() })).toHaveAttribute("href", "/")
    // The name is the way home, so the bar carries no second control that leads there, and no
    // second title beside it (specification 2026-09-21, decisions I, J and M).
    expect(screen.queryByRole("link", { name: m.home_heading() })).toBeNull()
    expect(screen.queryByRole("heading", { name: m.home_heading() })).toBeNull()
  })
})
