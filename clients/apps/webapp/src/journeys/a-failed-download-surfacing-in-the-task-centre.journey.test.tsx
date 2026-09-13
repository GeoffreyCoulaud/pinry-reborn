import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  download,
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  pin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

describe("a failed download surfacing in the task centre", () => {
  it("Given a download that failed, Then the centre says why and offers the recourse", async () => {
    const user = userEvent.setup()
    const failed = pin("a cat asleep", {
      status: "FAILED",
      reasonCode: "NOT_FOUND",
      message: "No image at this URL.",
    })
    let retried: string | null = null
    server.use(
      sessionRoute(() => true),
      onePinPage(() => [failed]),
      downloadsRoute(() => [download(failed.id, "FAILED", "The server could not fetch it.")]),
      handshakeRoute(),
      http.put("/api/v1/pins/:pinId/image", ({ params }) => {
        retried = String(params.pinId)
        return HttpResponse.json({ status: "PENDING" }, { status: 202 })
      }),
    )

    renderApp("/")
    await user.click(await screen.findByRole("button", { name: "Downloads (1)" }))

    expect(await screen.findByText("Failed")).toBeVisible()
    // The reason is read from `reasonCode` through the catalogue, not from the server's own
    // English sentence, which a French reader would otherwise get (specification 4.8).
    expect(screen.getByText("That download failed.")).toBeVisible()
    expect(screen.queryByText("The server could not fetch it.")).toBeNull()
    // The recourse question V exists for: the address again, a file from disk, or neither.
    expect(screen.getByLabelText("Image file")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Forget it" })).toBeVisible()

    await user.click(screen.getByRole("button", { name: "Try again" }))

    await waitFor(() => expect(retried).toBe(failed.id))
  })

  it("Given the creation screen, Then the centre is there too and carries the running download", async () => {
    const user = userEvent.setup()
    server.use(
      sessionRoute(() => true),
      downloadsRoute(() => [download("a-pin", "PENDING")]),
      handshakeRoute(),
    )

    renderApp("/pins/new")
    await user.click(await screen.findByRole("button", { name: "Downloads (1)" }))

    expect(await screen.findByText("Downloading")).toBeVisible()
  })

  it("Given actions the API refuses, Then the centre says so rather than staying silent", async () => {
    const user = userEvent.setup()
    const failed = pin("a cat asleep", { status: "FAILED", reasonCode: "FETCH_FAILED" })
    server.use(
      sessionRoute(() => true),
      onePinPage(() => [failed]),
      downloadsRoute(() => [download(failed.id, "FAILED")]),
      handshakeRoute(),
      http.put("/api/v1/pins/:pinId/image", () => new HttpResponse(null, { status: 503 })),
      http.delete("/api/v1/me/image-downloads/:pinId", () => new HttpResponse(null, { status: 503 })),
    )

    renderApp("/")
    await user.click(await screen.findByRole("button", { name: "Downloads (1)" }))

    await user.click(await screen.findByRole("button", { name: "Try again" }))
    expect(await screen.findByRole("alert")).toHaveTextContent("That image could not be added.")

    await user.click(screen.getByRole("button", { name: "Forget it" }))
    expect(await screen.findByText("That task could not be forgotten.")).toBeVisible()
  })

  it("Given a file uploaded from the centre, Then the grid rereads and the tile appears", async () => {
    const user = userEvent.setup()
    const failed = pin("a cat asleep", { status: "FAILED", reasonCode: "FETCH_FAILED" })
    const url = `/api/v1/pins/${failed.id}/image`
    const ready = { ...failed, image: { status: "READY" as const, url, width: 800, height: 600 } }
    let stored = false
    server.use(
      sessionRoute(() => true),
      onePinPage(() => [stored ? ready : failed]),
      // The upload clears the row on the server, so the list empties with it and a DELETE the
      // client sent afterwards would answer 404 (SetPinImage calls ClearPinDownload).
      downloadsRoute(() => (stored ? [] : [download(failed.id, "FAILED")])),
      handshakeRoute(),
      http.put("/api/v1/pins/:pinId/image", () => {
        stored = true
        return HttpResponse.json({ id: failed.id, pinId: failed.id }, { status: 200 })
      }),
      http.delete("/api/v1/me/image-downloads/:pinId", () => new HttpResponse(null, { status: 404 })),
    )

    renderApp("/")
    await user.click(await screen.findByRole("button", { name: "Downloads (1)" }))
    await user.upload(
      screen.getByLabelText("Image file"),
      new File(["ok"], "cat.png", { type: "image/png" }),
    )
    // Read by alt text and by text: react-aria calls `ariaHideOutside` while the popover is
    // open, so every role behind it is out of the accessibility tree until the popover closes,
    // and closing it on a keystroke is a race the gate's container loses.
    expect(await screen.findByAltText(failed.description)).toBeInTheDocument()
    expect(await screen.findByText("Downloads (0)")).toBeInTheDocument()
  }, 15_000)
})
