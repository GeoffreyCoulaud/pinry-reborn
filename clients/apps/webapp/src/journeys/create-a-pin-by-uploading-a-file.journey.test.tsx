import { fireEvent, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { afterEach, describe, expect, it, vi } from "vitest"
import {
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

/** The drop area's accessible name is its visible invitation, and nothing else names it. */
const DROP_AREA = "Drop an image here, or pick one"

/** The dialog is opened from the grid, and everything the form holds is queried inside it. */
async function openTheDialog(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "Add a pin" }))
  return within(await screen.findByRole("dialog", { name: "Add a pin" }))
}

afterEach(() => vi.restoreAllMocks())

describe("create a pin by uploading a file", () => {
  it("Given a file heavier than the deployment stores, Then no request leaves at all", async () => {
    const user = userEvent.setup()
    let requests = 0
    server.use(
      sessionRoute(() => true),
      handshakeRoute({ maxFileBytes: 4 }),
      downloadsRoute(),
      onePinPage(() => []),
      http.post("/api/v1/pins", () => {
        requests += 1
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.upload(
      dialog.getByLabelText(DROP_AREA),
      new File(["more than four bytes"], "big.png", { type: "image/png" }),
    )

    // The refusal is pronounced at the choice, and the file is not kept: the address is required
    // again, which is what stops the submission below from sending anything.
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This file is heavier than this server accepts.",
    )
    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(requests).toBe(0)
  })

  it("Given a file whose bytes decode to nothing, Then the area says so and keeps it", async () => {
    const user = userEvent.setup()
    vi.spyOn(globalThis, "createImageBitmap").mockRejectedValue(new Error("damaged"))
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => []),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.upload(
      dialog.getByLabelText(DROP_AREA),
      new File(["not a picture"], "damaged.png", { type: "image/png" }),
    )

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This image could not be read. Try another file.",
    )
  })

  it("Given anything but an image dropped on the area, Then it is not taken", async () => {
    const user = userEvent.setup()
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => []),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    const area = dialog.getByLabelText(DROP_AREA)
    // A drop bypasses `accept`, which only the file picker honours, so it is the only way in.
    fireEvent.drop(area, {
      dataTransfer: { files: [new File(["%PDF"], "notes.pdf", { type: "application/pdf" })] },
    })

    expect(await screen.findByRole("alert")).toHaveTextContent("This file is not an image.")
    expect(dialog.queryByText("notes.pdf")).toBeNull()
    // No file was kept, so the other way in is required again.
    expect(dialog.getByLabelText("Image address")).toBeRequired()
  })

  it("Given a drop carrying no file at all, Then it is refused and the choice stands", async () => {
    const user = userEvent.setup()
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => []),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.upload(
      dialog.getByLabelText(DROP_AREA),
      new File(["ok"], "small.png", { type: "image/png" }),
    )
    expect(await dialog.findByText("small.png")).toBeVisible()
    // Dragging an image out of another browser tab hands over an address and no file at all.
    fireEvent.drop(dialog.getByLabelText(DROP_AREA), { dataTransfer: { files: [] } })

    expect(await screen.findByRole("alert")).toHaveTextContent("This file is not an image.")
    // The drop took nothing, so it takes nothing away either.
    expect(dialog.getByText("small.png")).toBeVisible()
    expect(dialog.getByLabelText("Image address")).not.toBeRequired()
  })

  it("Given limits that arrive after the file, Then the refusal takes the file with it", async () => {
    const user = userEvent.setup()
    let publish = () => {}
    const published = new Promise<void>((resolve) => (publish = resolve))
    server.use(
      sessionRoute(() => true),
      downloadsRoute(),
      onePinPage(() => []),
      // The one case decision M leaves to the submission: the file was judged against limits
      // nobody had yet, so it was taken, and the answer arrives after it.
      http.get("/api/v1/handshake", async () => {
        await published
        return HttpResponse.json({
          contractVersion: "4.0.0",
          limits: { maxFileBytes: 4, maxPixels: 50_000_000 },
          renditionSizes: { tiny: 80, small: 240, medium: 640, large: 1600 },
        })
      }),
    )

    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.upload(
      dialog.getByLabelText(DROP_AREA),
      new File(["more than four bytes"], "big.png", { type: "image/png" }),
    )
    expect(await dialog.findByText("big.png")).toBeVisible()

    publish()
    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "This file is heavier than this server accepts.",
    )
    // The message names one recourse, choosing another file, so the screen states one thing too.
    expect(dialog.queryByText("big.png")).toBeNull()
    expect(dialog.getByLabelText("Image address")).toBeRequired()
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
    renderApp("/")
    const dialog = await openTheDialog(user)
    await user.upload(
      dialog.getByLabelText(DROP_AREA),
      new File(["ok"], "small.png", { type: "image/png" }),
    )
    // The thumbnail and the name are the only check that catches a wrong file before the upload.
    expect(await dialog.findByText("small.png")).toBeVisible()
    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // The grid is hidden from the reader while the dialog is open, so the tile answering at all
    // says the dialog closed on its own. No download and no wait: the bytes are the server's.
    expect(await screen.findByRole("img", { name: created.description })).toBeVisible()
    expect(uploaded).toBe("multipart/form-data")
    expect(sentPage).toBeNull()
    expect(await screen.findByRole("button", { name: "Downloads (0)" })).toBeVisible()
  }, 15_000)
})
