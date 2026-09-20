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

/** The drop area's accessible name is its visible invitation, and nothing else names it. */
const DROP_AREA = "Drop an image here, or pick one"

/** The whole screen is the drop target, and the heading is what names it from the inside. */
async function theScreen() {
  return (await screen.findByRole("heading", { name: "Your pins" })).closest("main") as HTMLElement
}

/** An image the thumbnail names, which is how one entry is told from the next. */
function image(name: string) {
  return new File(["ok"], name, { type: "image/png" })
}

/** The dialog, and everything the form holds queried inside it. */
async function theForm() {
  return within(await screen.findByRole("dialog", { name: "Add a pin" }))
}

describe("add several pins from one drop", () => {
  it("Given two images dropped at once, Then each is added in turn and the form says where it is", async () => {
    const user = userEvent.setup()
    const created = readyPin("a cat asleep")
    const sent: unknown[] = []
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => (sent.length === 2 ? [created] : [])),
      http.post("/api/v1/pins", async ({ request }) => {
        sent.push(await request.json())
        return HttpResponse.json(created, { status: 201 })
      }),
      http.put("/api/v1/pins/:pinId/image", () =>
        HttpResponse.json({ id: created.id, pinId: created.id }, { status: 201 }),
      ),
    )

    renderApp("/")
    // The drop is judged in full where it lands, so its own total is fixed before its first
    // entry is shown (specification 2026-09-19-the-drop-is-the-gesture, decision N).
    fireEvent.drop(await theScreen(), dropOf([image("one.png"), image("two.png")]))

    const dialog = await theForm()
    expect(await dialog.findByText("one.png")).toBeVisible()
    // The compact form is for the eye, and the sentence is what a screen reader is given.
    expect(dialog.getByRole("img", { name: "Pin 1 of 2" })).toHaveTextContent("1/2")

    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    // The queue advances on the server's word alone (decision K).
    expect(await dialog.findByText("two.png")).toBeVisible()
    expect(dialog.getByRole("img", { name: "Pin 2 of 2" })).toBeVisible()
    await user.click(dialog.getByRole("button", { name: "Add a pin" }))

    // The last entry taken closes the dialog, as a single entry always did.
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull())
    expect(sent).toHaveLength(2)
    expect(await screen.findByRole("img", { name: created.description })).toBeVisible()
  }, 15_000)

  it("Given an entry ignored, Then the form moves on and nothing is sent for it", async () => {
    const user = userEvent.setup()
    let requests = 0
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => []),
      http.post("/api/v1/pins", () => {
        requests += 1
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    renderApp("/")
    fireEvent.drop(await theScreen(), dropOf([image("one.png"), image("two.png")]))

    const dialog = await theForm()
    expect(await dialog.findByText("one.png")).toBeVisible()
    // Two verbs, two effects: `Remove` empties this entry, `Ignore` abandons it (decision I).
    await user.click(dialog.getByRole("button", { name: "Ignore" }))

    expect(await dialog.findByText("two.png")).toBeVisible()
    expect(dialog.getByRole("img", { name: "Pin 2 of 2" })).toBeVisible()
    expect(dialog.queryByText("one.png")).toBeNull()
    expect(requests).toBe(0)
  })

  it("Given an entry the server refused, Then the refusal does not follow the next one", async () => {
    const user = userEvent.setup()
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => []),
      http.post("/api/v1/pins", () => new HttpResponse(null, { status: 500 })),
    )

    renderApp("/")
    fireEvent.drop(await theScreen(), dropOf([image("one.png"), image("two.png")]))

    const dialog = await theForm()
    const submit = dialog.getByRole("button", { name: "Add a pin" })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await dialog.findByRole("alert")).toHaveTextContent("That pin could not be added.")

    // The message belongs to the entry that earned it, and nothing has been sent for the next one.
    await user.click(dialog.getByRole("button", { name: "Ignore" }))
    expect(await dialog.findByText("two.png")).toBeVisible()
    expect(dialog.queryByRole("alert")).toBeNull()
  })

  it("Given more images chosen on an open form, Then they join the end of the queue", async () => {
    const user = userEvent.setup()
    server.use(
      sessionRoute(() => true),
      handshakeRoute(),
      downloadsRoute(),
      onePinPage(() => []),
    )

    renderApp("/")
    fireEvent.drop(await theScreen(), dropOf([image("one.png")]))

    const dialog = await theForm()
    expect(await dialog.findByText("one.png")).toBeVisible()
    // One entry is no group, so it says nothing about one (decisions I and J).
    expect(dialog.queryByRole("img", { name: /^Pin/ })).toBeNull()
    expect(dialog.queryByRole("button", { name: "Ignore" })).toBeNull()

    // What is chosen with the mouse is judged where what is dropped is (decision L), and several
    // arriving are "add these two" rather than a correction of the entry on screen (decision H).
    await user.upload(dialog.getByLabelText(DROP_AREA), [image("two.png"), image("three.png")])

    expect(await dialog.findByRole("img", { name: "Pin 1 of 3" })).toBeVisible()
    // The total moved because the user dropped more, and the entry being worked on is untouched.
    expect(dialog.getByText("one.png")).toBeVisible()
  })
})
