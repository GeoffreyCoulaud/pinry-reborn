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
const PASSWORD = "correct horse"

/** The refusal as the API sends one: a problem body whose `code` is what the dialog reads. */
function refused(status: number, code: string) {
  return HttpResponse.json({ status, code }, { status })
}

/** The account screen, opened on a session the API still honours. */
async function openTheDialog() {
  renderApp("/account")
  const user = userEvent.setup()
  await user.click(await screen.findByRole("button", { name: m.delete_account() }))
  return user
}

describe("delete the account", () => {
  it("Given the password, Then the account goes and the session with it", async () => {
    let open = true
    let renewals = 0
    let renewalsWhenSigningIn: number | null = null
    let factor: string | null = null
    server.use(
      sessionRoute(() => open, DUE_SESSION),
      http.post("/api/v1/sessions/current/renew", () => {
        renewals += 1
        return HttpResponse.json(DUE_SESSION)
      }),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.delete("/api/v1/me", ({ request }) => {
        factor = request.headers.get("X-Reauthentication")
        open = false
        return new HttpResponse(null, { status: 202 })
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
    const user = await openTheDialog()

    await user.type(await screen.findByLabelText(m.password()), PASSWORD)
    await user.click(screen.getByRole("button", { name: m.delete_account_confirm() }))

    expect(await screen.findByRole("heading", { name: m.sign_in() })).toBeVisible()
    // The kind the API's parser reads, and the password base64url of its UTF-8 after it.
    expect(factor).toBe("password Y29ycmVjdCBob3JzZQ==")

    // As on every write here: left believing in a session, the package would spend a renewal on
    // the credentials screen's first request (specification 2026-09-22, decision E).
    renewals = 0
    await user.type(screen.getByLabelText(m.username()), ACCOUNT.name)
    await user.type(screen.getByLabelText(m.password()), PASSWORD)
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    expect(await screen.findByRole("button", { name: m.create_pin() })).toBeVisible()
    expect(renewalsWhenSigningIn).toBe(0)
  })

  it("Given the dialog dismissed, Then the account is untouched", async () => {
    let deletions = 0
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.delete("/api/v1/me", () => {
        deletions += 1
        return new HttpResponse(null, { status: 202 })
      }),
      downloadsRoute(),
    )
    const user = await openTheDialog()

    await user.click(await screen.findByRole("button", { name: m.cancel() }))

    expect(screen.queryByRole("button", { name: m.delete_account_confirm() })).toBeNull()
    expect(deletions).toBe(0)
  })

  it("Given the wrong password, Then the dialog stays open and says which refusal it was", async () => {
    let attempts = 0
    server.use(
      sessionRoute(() => true),
      http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
      http.delete("/api/v1/me", () => {
        attempts += 1
        return attempts === 1
          ? refused(403, "REAUTHENTICATION_FAILED")
          : refused(429, "TOO_MANY_AUTHENTICATION_ATTEMPTS")
      }),
      downloadsRoute(),
    )
    const user = await openTheDialog()

    await user.type(await screen.findByLabelText(m.password()), "wrong")
    await user.click(screen.getByRole("button", { name: m.delete_account_confirm() }))

    expect(await screen.findByRole("alert")).toHaveTextContent(m.password_wrong())
    // Still open, and the account still there: a dialog that closed would take the retry with it.
    expect(screen.getByRole("button", { name: m.delete_account_confirm() })).toBeVisible()

    await user.click(screen.getByRole("button", { name: m.delete_account_confirm() }))

    // The limiter's own sentence: what is read is the code the body carries, not the status.
    expect(await screen.findByText(m.too_many_attempts())).toBeVisible()
    expect(screen.queryByText(m.password_wrong())).toBeNull()
    expect(attempts).toBe(2)
  })
})
