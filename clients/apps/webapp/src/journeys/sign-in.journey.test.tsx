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

describe("sign in", () => {
  it("Given credentials the API accepts, Then the home screen is on screen", async () => {
    let opened: Record<string, unknown> | undefined
    server.use(
      http.post("/api/v1/sessions", async ({ request }) => {
        opened = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(SESSION)
      }),
      sessionRoute(() => opened !== undefined),
      pinsRoute([]),
      downloadsRoute(),
      handshakeRoute(),
    )
    renderApp("/sign-in")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.username()), "ada")
    await user.type(screen.getByLabelText(m.password()), "correct horse")
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    // The bar's name heads the credentials screen too, so the home screen's own control is what
    // says the session opened (specification 2026-09-21, decision I').
    expect(await screen.findByRole("button", { name: m.create_pin() })).toBeVisible()
    // The transport is the whole point of the package: a browser cannot send a header on an <img>.
    expect(opened).toEqual({ name: "ada", password: "correct horse", transport: "COOKIE", rememberMe: false })
  })

  // The handler reads the checkbox out of `FormData` by name, and an unticked one is absent either
  // way: only a ticked one says the control still carries the field.
  it("Given the remember me box ticked, Then the session is opened to last", async () => {
    let opened: Record<string, unknown> | undefined
    server.use(
      http.post("/api/v1/sessions", async ({ request }) => {
        opened = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(SESSION)
      }),
      sessionRoute(() => opened !== undefined),
      pinsRoute([]),
      downloadsRoute(),
      handshakeRoute(),
    )
    renderApp("/sign-in")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.username()), "ada")
    await user.type(screen.getByLabelText(m.password()), "correct horse")
    await user.click(screen.getByRole("checkbox", { name: m.remember_me() }))
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    expect(await screen.findByRole("button", { name: m.create_pin() })).toBeVisible()
    expect(opened).toEqual({
      name: "ada",
      password: "correct horse",
      transport: "COOKIE",
      rememberMe: true,
    })
  })

  it("Given a password the API refuses, Then the screen says so and stays", async () => {
    server.use(http.post("/api/v1/sessions", () => new HttpResponse(null, { status: 401 })))
    renderApp("/sign-in")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(m.username()), "ada")
    await user.type(screen.getByLabelText(m.password()), "wrong")
    await user.click(screen.getByRole("button", { name: m.sign_in() }))

    expect(await screen.findByRole("alert")).toHaveTextContent(m.sign_in_refused())
  })
})
