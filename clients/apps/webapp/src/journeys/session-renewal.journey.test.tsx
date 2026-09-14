import { screen, waitFor } from "@testing-library/react"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  DUE_SESSION,
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  readyPin,
  renderApp,
  renewRoute,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

describe("session renewal", () => {
  it("Given a session the API says is due, Then the calls behind it renew it once", async () => {
    const ready = readyPin("a harbour at dusk")
    let renewals = 0
    server.use(
      sessionRoute(() => true, DUE_SESSION),
      renewRoute(() => (renewals += 1)),
      pinsRoute([[ready]]),
      downloadsRoute(),
      handshakeRoute(),
    )

    renderApp("/")

    expect(await screen.findByRole("img", { name: ready.description })).toBeVisible()
    // The grid, the task centre and the handshake leave together: an unshared renewal is three.
    await waitFor(() => expect(renewals).toBe(1))
  })

  it("Given the renewal refused, Then the call that waited for it still goes out", async () => {
    const ready = readyPin("a harbour at dusk")
    server.use(
      sessionRoute(() => true, DUE_SESSION),
      http.post("/api/v1/sessions/current/renew", () => new HttpResponse(null, { status: 503 })),
      pinsRoute([[ready]]),
      downloadsRoute(),
      handshakeRoute(),
    )

    renderApp("/")

    expect(await screen.findByRole("img", { name: ready.description })).toBeVisible()
  })
})
