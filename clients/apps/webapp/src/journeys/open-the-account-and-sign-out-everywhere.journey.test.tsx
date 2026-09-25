import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  DUE_SESSION,
  SESSION,
  downloadsRoute,
  exportsRoute,
  handshakeRoute,
  importsRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }

describe("open the account and sign out everywhere", () => {
  it("Given an open session, Then the account is reachable and every session closes at once", async () => {
    let open = true
    let revocations = 0
    let renewals = 0
    let renewalsWhenSigningIn: number | null = null
    server.use(
      sessionRoute(() => open, DUE_SESSION),
      // The renewal answers a session still due, so `renewAfter` is in the past at every request.
      // What tells `forget()` apart from its absence is then whether the package holds one at all.
      http.post("/api/v1/sessions/current/renew", () => {
        renewals += 1
        return HttpResponse.json(DUE_SESSION)
      }),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.delete("/api/v1/sessions", () => {
        revocations += 1
        open = false
        return new HttpResponse(null, { status: 204 })
      }),
      http.post("/api/v1/sessions", () => {
        renewalsWhenSigningIn = renewals
        open = true
        return HttpResponse.json(SESSION)
      }),
      pinsRoute([]),
      downloadsRoute(),
      exportsRoute(),
      importsRoute(),
      handshakeRoute(),
    )
    renderApp("/")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("link", { name: m.account() }))

    // The account's own name and not the product's: the heading is what `GET /api/v1/me` answered.
    expect(await screen.findByRole("heading", { name: ACCOUNT.name })).toBeVisible()

    await user.click(screen.getByRole("button", { name: m.sign_out_everywhere() }))

    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()
    expect(revocations).toBe(1)

    // The credentials screen's first request is the discriminator: left believing in a session,
    // the package spends a renewal on it that the API refuses (specification 2026-09-22,
    // decision E). It is counted where that request lands, so what the home screen behind it then
    // asks for cannot answer for it.
    renewals = 0
    await user.type(screen.getByLabelText(m.username()), ACCOUNT.name)
    await user.type(screen.getByLabelText(m.password()), "correct horse")
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    expect(await screen.findByRole("button", { name: m.create_pin() })).toBeVisible()
    expect(renewalsWhenSigningIn).toBe(0)
  })

  it("Given the API refusing to close them, Then the screen says so and stays", async () => {
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.delete("/api/v1/sessions", () => new HttpResponse(null, { status: 500 })),
      downloadsRoute(),
      exportsRoute(),
      importsRoute(),
      handshakeRoute(),
    )
    renderApp("/account")
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: m.sign_out_everywhere() }))

    expect(await screen.findByRole("alert")).toHaveTextContent(m.sign_out_everywhere_refused())
    expect(screen.getByRole("heading", { name: ACCOUNT.name })).toBeVisible()
  })

  it("Given an account the API cannot answer for, Then the screen says so", async () => {
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/me", () => new HttpResponse(null, { status: 500 })),
      downloadsRoute(),
      exportsRoute(),
      importsRoute(),
      handshakeRoute(),
    )
    renderApp("/account")

    // Every other screen names a read it could not make; a plausible heading over a password form
    // about to fail for the same reason is what this one showed instead.
    expect(await screen.findByRole("alert")).toHaveTextContent(m.account_unreadable())
  })

  it("Given no session, Then the account screen is the credentials screen", async () => {
    server.use(sessionRoute(() => false))
    renderApp("/account")

    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()
    // A screen rendering empty would pass the heading above on its own: the control that is not
    // there is what says the account never rendered.
    expect(screen.queryByRole("button", { name: m.sign_out_everywhere() })).toBeNull()
  })
})
