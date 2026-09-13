import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const PAGE = "https://example.test/page"

describe("create a pin by uploading a file", () => {
  it("Given a file heavier than the deployment stores, Then no request leaves at all", async () => {
    const user = userEvent.setup()
    let requests = 0
    server.use(
      sessionRoute(() => true),
      handshakeRoute({ maxFileBytes: 4 }),
      http.post("/api/v1/pins", () => {
        requests += 1
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    renderApp("/pins/new")
    await user.type(await screen.findByLabelText("Page it comes from"), PAGE)
    await user.upload(
      screen.getByLabelText("Image file"),
      new File(["more than four bytes"], "big.png", { type: "image/png" }),
    )
    const submit = screen.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This file is heavier than this server accepts.",
    )
    expect(requests).toBe(0)
  })

  it("Given a file the deployment stores, Then the tile is in the grid at once", async () => {
    const user = userEvent.setup()
    const created = readyPin("a cat asleep")
    let uploaded: string | null = null
    let sentPage: unknown = "not sent"
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => [created]),
      http.post("/api/v1/pins", async ({ request }) => {
        sentPage = ((await request.json()) as { sourceContextUrl: unknown }).sourceContextUrl
        return HttpResponse.json(created, { status: 201 })
      }),
      http.put("/api/v1/pins/:pinId/image", ({ request }) => {
        // The media type is what tells the two entries apart on one route, and it is all this
        // reads: reading the parts back costs the body, which a jsdom upload does not survive
        // the same way on every Node the gate and a workstation run.
        uploaded = request.headers.get("content-type")?.split(";")[0] ?? null
        return HttpResponse.json({ id: created.id, pinId: created.id }, { status: 201 })
      }),
    )

    // The page it comes from is left empty: a file from disk was found on no page at all.
    renderApp("/pins/new")
    await screen.findByLabelText("Page it comes from")
    await user.upload(
      screen.getByLabelText("Image file"),
      new File(["ok"], "small.png", { type: "image/png" }),
    )
    const submit = screen.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // No download and no wait: the bytes are the server's before the pin leaves the screen.
    expect(await screen.findByRole("img", { name: created.description })).toBeVisible()
    expect(uploaded).toBe("multipart/form-data")
    expect(sentPage).toBeNull()
    expect(await screen.findByRole("button", { name: "Downloads (0)" })).toBeVisible()
  }, 15_000)
})
