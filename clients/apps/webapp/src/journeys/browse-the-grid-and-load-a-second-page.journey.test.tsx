import { screen, waitFor } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import {
  downloadsRoute,
  handshakeRoute,
  pin,
  pinsRoute,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

describe("browse the grid and load a second page", () => {
  it("Given a catalogue of two pages, Then the end of the first brings the second", async () => {
    const first = readyPin("a harbour at dusk")
    const second = readyPin("a cat asleep")
    server.use(sessionRoute(() => true), pinsRoute([[first], [second]]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByRole("img", { name: first.description })).toBeVisible()
    expect(await screen.findByRole("img", { name: second.description })).toBeVisible()
  })

  it("Given two pages, Then the grid asks the API for each of them once", async () => {
    const first = readyPin("a harbour at dusk")
    const second = readyPin("a cat asleep")
    let requests = 0
    server.use(
      sessionRoute(() => true),
      pinsRoute([[first], [second]], () => {
        requests += 1
      }),
      downloadsRoute(),
      handshakeRoute(),
    )

    renderApp("/")

    expect(await screen.findByRole("img", { name: second.description })).toBeVisible()
    // The sentinel is re-observed on every collection change, its own loading flag being one
    // such change, so an unguarded `onLoadMore` asks again for a page the grid already holds.
    expect(requests).toBe(2)
  })

  it("Given six pages, Then the first page's tiles survive scrolling to the end", async () => {
    const pages = Array.from({ length: 6 }, (_, index) => [readyPin(`page ${index + 1}`)])
    server.use(sessionRoute(() => true), pinsRoute(pages), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByRole("img", { name: "page 6" })).toBeVisible()
    // A cap on the infinite query drops the pages past it and nothing reloads them, which is
    // why the grid holds every page and the virtualiser bounds the memory (ADR 0033).
    expect(screen.getByRole("img", { name: "page 1" })).toBeVisible()
  })

  it("Given the grid, Then the deployment's own rendition sizes are what the tile reads", async () => {
    const ready = readyPin("a harbour at dusk")
    let asked = 0
    server.use(
      sessionRoute(() => true),
      pinsRoute([[ready]]),
      downloadsRoute(),
      handshakeRoute({ small: 120, onRequest: () => (asked += 1) }),
    )

    renderApp("/")

    expect(await screen.findByRole("img", { name: ready.description })).toBeVisible()
    // The breakpoint was a constant no route published, which a deployment narrowing `small`
    // then upscaled every tile against (specification 2026-09-10, 4.3).
    await waitFor(() => expect(asked).toBe(1))
  })

  it("Given an image the API measured, Then the tile is placed at that ratio before it loads", async () => {
    const wide = readyPin("a harbour at dusk", 800, 600)
    server.use(sessionRoute(() => true), pinsRoute([[wide]]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    const tile = await screen.findByRole("img", { name: wide.description })
    expect(tile).toHaveStyle({ aspectRatio: "800 / 600" })
    // The column is unmeasurable in jsdom, so the narrowest rendition is what a zero width asks for.
    expect(tile).toHaveAttribute("src", `/api/v1/pins/${wide.id}/image?size=SMALL`)
  })

  it("Given a download the server is still running, Then no tile stands for the pin", async () => {
    const ready = readyPin("a harbour at dusk")
    const pending = pin("a cat asleep", { status: "PENDING" })
    server.use(sessionRoute(() => true), pinsRoute([[ready, pending]]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByRole("img", { name: ready.description })).toBeVisible()
    expect(screen.queryByText(pending.description)).toBeNull()
  })

  it("Given a download that failed, Then its tile carries the reason in the reader's language", async () => {
    const failed = pin("a cat asleep", {
      status: "FAILED",
      reasonCode: "NOT_FOUND",
      message: "No image at this URL.",
    })
    server.use(sessionRoute(() => true), pinsRoute([[failed]]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByText("There is no image at that address.")).toBeVisible()
    expect(screen.queryByText("No image at this URL.")).toBeNull()
  })

  it("Given a reason this bundle has no key for, Then the server's own sentence is shown", async () => {
    const failed = pin("a cat asleep", {
      status: "FAILED",
      reasonCode: "A_REASON_ADDED_AFTER_THIS_BUNDLE",
      message: "Something else went wrong.",
    })
    server.use(sessionRoute(() => true), pinsRoute([[failed]]), downloadsRoute(), handshakeRoute())

    renderApp("/")

    expect(await screen.findByText("Something else went wrong.")).toBeVisible()
  })
})
