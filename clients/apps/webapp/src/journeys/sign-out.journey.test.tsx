import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  SESSION,
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

describe("sign out", () => {
  it("Given an open session, Then signing out asks for the credentials again", async () => {
    let open = true
    server.use(
      sessionRoute(() => open),
      pinsRoute([]),
      downloadsRoute(),
      handshakeRoute(),
      http.delete("/api/v1/sessions/current", () => {
        open = false
        return new HttpResponse(null, { status: 204 })
      }),
    )
    renderApp("/")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: m.sign_out() }))

    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()
    expect(open).toBe(false)
  })

  it("Given the next account signing in, Then the grid holds nothing of the previous one's", async () => {
    const theirs = readyPin("a harbour at dusk")
    let open = true
    // The second account's page is held rather than delayed: what the grid paints in that window
    // is the cache and nothing else, and a duration would race the assertion.
    let holding = false
    let release = () => {}
    const held = new Promise<void>((resolve) => {
      release = resolve
    })
    server.use(
      sessionRoute(() => open),
      http.get("/api/v1/pins", async () => {
        if (holding) await held
        return HttpResponse.json({
          pins: [theirs],
          pagination: { previousCursor: null, nextCursor: null },
        })
      }),
      downloadsRoute(),
      handshakeRoute(),
      http.delete("/api/v1/sessions/current", () => {
        open = false
        return new HttpResponse(null, { status: 204 })
      }),
      http.post("/api/v1/sessions", () => {
        open = true
        return HttpResponse.json(SESSION)
      }),
    )
    renderApp("/")
    const user = userEvent.setup()

    expect(await screen.findByRole("img", { name: theirs.description })).toBeVisible()
    await user.click(screen.getByRole("button", { name: m.sign_out() }))
    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()

    holding = true
    await user.type(screen.getByLabelText(m.username()), "grace")
    await user.type(screen.getByLabelText(m.password()), "correct horse")
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    expect(await screen.findByRole("status")).toHaveTextContent(m.pins_loading())
    expect(screen.queryByRole("img", { name: theirs.description })).toBeNull()
    release()
  })
})
