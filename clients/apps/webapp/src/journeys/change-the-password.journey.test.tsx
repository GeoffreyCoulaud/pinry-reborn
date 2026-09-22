import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  DUE_SESSION,
  SESSION,
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }
const OLD = "correct horse"
const NEW = "battery staple"

/** The refusal as the API sends one: a problem body whose `code` is what the screen reads. */
function refused(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status })
}

describe("change the password", () => {
  it("Given the current password, Then it changes and every session closes with it", async () => {
    let open = true
    let renewals = 0
    let renewalsWhenSigningIn: number | null = null
    let changed: unknown = null
    server.use(
      sessionRoute(() => open, DUE_SESSION),
      // The renewal answers a session still due, so `renewAfter` is in the past at every request
      // and what tells `forget()` apart from its absence is whether the package holds one at all.
      http.post("/api/v1/sessions/current/renew", () => {
        renewals += 1
        return HttpResponse.json(DUE_SESSION)
      }),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.put("/api/v1/me/password", async ({ request }) => {
        changed = await request.json()
        // The API revokes inside the write's transaction, so the session is dead on its answer.
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
      handshakeRoute(),
    )
    renderApp("/account")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.current_password()), OLD)
    await user.type(screen.getByLabelText(m.new_password()), NEW)
    await user.click(screen.getByRole("button", { name: m.change_password() }))

    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()
    expect(changed).toEqual({ currentPassword: OLD, newPassword: NEW })

    // The credentials screen's first request is the discriminator: left believing in a session,
    // the package spends a renewal on it that the API refuses (specification 2026-09-22,
    // decision E).
    renewals = 0
    await user.type(screen.getByLabelText(m.username()), ACCOUNT.name)
    await user.type(screen.getByLabelText(m.password()), NEW)
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    expect(await screen.findByRole("button", { name: m.create_pin() })).toBeVisible()
    expect(renewalsWhenSigningIn).toBe(0)
  })

  it("Given a change the API judges too soon, Then that refusal is the one named", async () => {
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.put("/api/v1/me/password", () => refused(429, "PASSWORD_CHANGED_TOO_SOON")),
      downloadsRoute(),
    )
    renderApp("/account")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.current_password()), OLD)
    await user.type(screen.getByLabelText(m.new_password()), NEW)
    await user.click(screen.getByRole("button", { name: m.change_password() }))

    // The attempt limiter answers 429 too, so a screen reading the status shows its sentence here.
    expect(await screen.findByRole("alert")).toHaveTextContent(m.password_changed_too_soon())
    expect(screen.queryByText(m.too_many_attempts())).toBeNull()
    // Refused, the write revoked nothing: the account screen is still the one being shown.
    expect(screen.getByRole("heading", { name: ACCOUNT.name })).toBeVisible()
  })
})
