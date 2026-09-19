import { fireEvent, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import {
  downloadsRoute,
  dropOf,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

/** Where the picture was found, which a picture dragged out of another tab carries with it. */
const FOUND_AT = "https://example.test/cat.png"

/** The whole screen is the drop target, and the heading is what names it from the inside. */
async function theScreen() {
  return (await screen.findByRole("heading", { name: "Your pins" })).closest("main") as HTMLElement
}

describe("drop an image on the grid to add a pin", () => {
  it("Given something dragged over the screen, Then the screen offers to take it", async () => {
    server.use(sessionRoute(() => true), handshakeRoute(), downloadsRoute(), onePinPage(() => []))

    renderApp("/")
    const main = await theScreen()

    // jsdom computes no style, so the overlay is read by its being in the document at all, which
    // is behaviour rather than paint.
    fireEvent.dragEnter(main)
    expect(await screen.findByText("Drop an image here to add a pin")).toBeVisible()

    fireEvent.dragLeave(main)
    await waitFor(() =>
      expect(screen.queryByText("Drop an image here to add a pin")).toBeNull(),
    )
  })

  it("Given an image dropped on the grid, Then the form opens holding it and the pin is added", async () => {
    const user = userEvent.setup()
    const created = readyPin("a cat asleep")
    let sent: unknown = "not sent"
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => [created]),
      http.post("/api/v1/pins", async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json(created, { status: 201 })
      }),
      http.put("/api/v1/pins/:pinId/image", () =>
        HttpResponse.json({ id: created.id, pinId: created.id }, { status: 201 }),
      ),
    )

    renderApp("/")
    // A picture dragged out of another browser tab hands over the bytes and the address it was
    // found at, and the second is provenance the server stores (decision G).
    fireEvent.drop(
      await theScreen(),
      dropOf([new File(["ok"], "cat.png", { type: "image/png" })], FOUND_AT),
    )

    const dialog = within(await screen.findByRole("dialog", { name: "Add a pin" }))
    // The form opens on what the drop kept: nothing is chosen a second time by hand.
    expect(await dialog.findByText("cat.png")).toBeVisible()
    expect(dialog.getByLabelText("Image address")).toHaveValue(FOUND_AT)

    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(await screen.findByRole("img", { name: created.description })).toBeVisible()
    expect(sent).toEqual({ sourceContextUrl: null, sourceMediaUrl: FOUND_AT, description: "" })
  }, 15_000)

  it("Given a drop nothing in it could become a pin, Then no form opens and the screen says so", async () => {
    server.use(sessionRoute(() => true), handshakeRoute(), downloadsRoute(), onePinPage(() => []))

    renderApp("/")
    // A tab dragging an image it holds in memory hands over a `blob:`, which names a picture no
    // server can reach and no file at all.
    fireEvent.drop(await theScreen(), dropOf([], "blob:https://example.test/0f5c"))

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Nothing in that drop could become a pin.",
    )
    // A drop that kept nothing has said so where it happened, and opens no form to empty.
    expect(screen.queryByRole("dialog")).toBeNull()
  })

  it("Given an image dropped past the screen's own box, Then the form opens on it all the same", async () => {
    const created = readyPin("a cat asleep")
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => [created]),
    )

    renderApp("/")
    await theScreen()
    // `<main>` is one screen tall, so a page scrolled past it leaves the pointer over the body,
    // where a target that is an element cancels nothing and the browser opens the image in the
    // tab. Seen in LibreWolf on 2026-09-19; the window is the target for this reason.
    fireEvent.drop(document.body, dropOf([new File(["ok"], "cat.png", { type: "image/png" })], ""))

    const dialog = within(await screen.findByRole("dialog", { name: "Add a pin" }))
    expect(await dialog.findByText("cat.png")).toBeVisible()
  })
})
