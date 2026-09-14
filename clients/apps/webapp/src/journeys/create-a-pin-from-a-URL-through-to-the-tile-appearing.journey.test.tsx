import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  download,
  downloadsPage,
  handshakeRoute,
  pin,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

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
    // the click. Polls before the request are the creation screen's own, and settle nothing.
    let requested = false
    let polls = 0
    let pageRequests = 0
    const settled = () => polls > 1
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      pinsRoute([[bare]], () => (pageRequests += 1)),
      http.get("/api/v1/pins/:pinId", () => HttpResponse.json(ready)),
      http.post("/api/v1/pins", () => HttpResponse.json(bare, { status: 201 })),
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

    renderApp("/pins/new")
    await user.type(await screen.findByLabelText("Page it comes from"), "https://example.test/page")
    await user.type(screen.getByLabelText("Image address"), "https://example.test/i.png")
    const submit = screen.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // The pin exists and its image does not: the work shows in the header, not in the grid.
    expect(await screen.findByRole("button", { name: "Downloads (1)" })).toBeVisible()
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
})
