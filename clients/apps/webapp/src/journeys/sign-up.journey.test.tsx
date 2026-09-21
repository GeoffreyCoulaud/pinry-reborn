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
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

describe("sign up", () => {
  it("Given a fresh instance, Then creating an account lands on the home screen", async () => {
    let created: Record<string, unknown> | undefined
    let opened = false
    server.use(
      http.post("/api/v1/users", async ({ request }) => {
        created = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: crypto.randomUUID(), name: created["name"] })
      }),
      http.post("/api/v1/sessions", () => {
        opened = true
        return HttpResponse.json(SESSION)
      }),
      sessionRoute(() => opened),
      pinsRoute([]),
      downloadsRoute(),
      handshakeRoute(),
    )
    renderApp("/sign-up")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.username()), "ada")
    await user.type(screen.getByLabelText(m.password()), "correct horse")
    await user.click(screen.getByRole("button", { name: m.sign_up() }))

    // The bar's name heads the credentials screen too, so the home screen's own control is what
    // says the session opened (specification 2026-09-21, decision I').
    expect(await screen.findByRole("button", { name: m.create_pin() })).toBeVisible()
    expect(created).toEqual({ name: "ada", password: "correct horse" })
  })

  it("Given a name the API refuses, Then the screen says so and opens no session", async () => {
    let opened = false
    server.use(
      http.post("/api/v1/users", () => new HttpResponse(null, { status: 409 })),
      http.post("/api/v1/sessions", () => {
        opened = true
        return HttpResponse.json(SESSION)
      }),
    )
    renderApp("/sign-up")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.username()), "ada")
    await user.type(screen.getByLabelText(m.password()), "correct horse")
    await user.click(screen.getByRole("button", { name: m.sign_up() }))

    expect(await screen.findByRole("alert")).toHaveTextContent(m.sign_up_refused())
    // A session opened despite the refusal would have signed the user in and left this screen.
    expect(opened).toBe(false)
  })
})
