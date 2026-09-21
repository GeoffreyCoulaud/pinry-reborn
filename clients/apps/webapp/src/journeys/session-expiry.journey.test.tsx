import { screen } from "@testing-library/react"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import { renderApp, sessionRoute } from "../test/app"
import { server } from "../test/server"

describe("session expiry", () => {
  it("Given a session the API no longer honours, Then the pins are never shown", async () => {
    server.use(sessionRoute(() => false))
    renderApp("/")

    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()
    // The name heads the credentials screen too, so what says the grid never came is the grid's
    // own control (specification 2026-09-21, decision I').
    expect(screen.queryByRole("button", { name: m.create_pin() })).toBeNull()
  })

  it("Given the session route failing, Then the user is told rather than signed out", async () => {
    server.use(http.get("/api/v1/sessions/current", () => new HttpResponse(null, { status: 503 })))
    renderApp("/")

    expect(await screen.findByRole("alert")).toHaveTextContent(m.session_unreadable())
    expect(screen.queryByRole("heading", { name: m.sign_in() })).toBeNull()
  })
})
