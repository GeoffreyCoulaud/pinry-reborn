import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  download,
  downloadsPage,
  handshakeRoute,
  onePinPage,
  pin,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

/** The dialog is opened from the grid, and everything the form holds is queried inside it. */
async function openTheDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "Add a pin" }))
  return within(await screen.findByRole("dialog", { name: "Add a pin" }))
}

describe("create a pin from a URL through to the tile appearing", () => {
  it("Given an address, Then the task centre holds the work and the tile follows it", async () => {
    const user = userEvent.setup()
    const bare = pin("a harbour at dusk", { status: "PENDING" })
    const ready = {
      ...bare,
      image: { status: "READY" as const, url: `/api/v1/pins/${bare.id}/image`, width: 800, height: 600 },
    }
    // The download settles on the second poll that follows the request, so the first render of
    // the grid is the one that has nothing to show: what the tile waits for is the server, not
    // the click. Polls before the request are the grid's own, and settle nothing.
    let requested = false
    let polls = 0
    let pageRequests = 0
    let sent: Record<string, unknown> | undefined
    const settled = () => polls > 1
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      pinsRoute([[bare]], () => (pageRequests += 1)),
      http.get("/api/v1/pins/:pinId", () => HttpResponse.json(ready)),
      http.post("/api/v1/pins", async ({ request }) => {
        sent = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(bare, { status: 201 })
      }),
      http.put("/api/v1/pins/:pinId/image", () => {
        requested = true
        return HttpResponse.json({ status: "PENDING" }, { status: 202 })
      }),
      http.get("/api/v1/me/image-downloads", () => {
        if (requested) polls += 1
        const running = requested && !settled()
        return HttpResponse.json(downloadsPage(running ? [download(bare.id, "PENDING")] : []))
      }),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.type(dialog.getByLabelText("Page it comes from"), "https://example.test/page")
    await user.type(dialog.getByLabelText("Description"), bare.description)
    await user.type(dialog.getByLabelText("Image address"), "https://example.test/i.png")
    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // The form is read by name out of `FormData`, and a name that no longer matches sends the
    // string "null" without failing anything downstream.
    expect(await screen.findByRole("button", { name: "Downloads (1)" })).toBeVisible()
    expect(sent).toEqual({
      sourceContextUrl: "https://example.test/page",
      description: bare.description,
      sourceMediaUrl: "https://example.test/i.png",
    })

    // The pin exists and its image does not: the work shows in the header, not in the grid.
    expect(screen.queryByRole("img", { name: bare.description })).toBeNull()

    // The settlement reads the one pin it changed and writes it into the pages already held: a
    // catalogue asked again here would cost one request per page scrolled.
    const paged = pageRequests
    const tile = await screen.findByRole("img", { name: bare.description }, { timeout: 4000 })
    expect(tile).toBeVisible()
    expect(pageRequests).toBe(paged)
    expect(await screen.findByRole("button", { name: "Downloads (0)" })).toBeVisible()

    // The list emptied, so nothing asks for it again.
    const asked = polls
    await new Promise((resolve) => setTimeout(resolve, 1200))
    expect(polls).toBe(asked)
  }, 15_000)

  // The address stops being required only once a file is chosen, and nothing else enforces it.
  it("Given neither an address nor a file, Then nothing is sent", async () => {
    const user = userEvent.setup()
    let created = 0
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      onePinPage(() => []),
      http.get("/api/v1/me/image-downloads", () => HttpResponse.json(downloadsPage([]))),
      http.post("/api/v1/pins", () => {
        created += 1
        return HttpResponse.json(pin("never asked for"), { status: 201 })
      }),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.type(dialog.getByLabelText("Page it comes from"), "https://example.test/page")
    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(created).toBe(0)
    expect(screen.getByRole("dialog", { name: "Add a pin" })).toBeVisible()
  })
})
