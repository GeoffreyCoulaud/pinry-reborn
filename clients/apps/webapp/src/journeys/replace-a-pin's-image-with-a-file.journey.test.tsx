import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import type { Pin } from "../pins"
import {
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  onePinPage,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

/** Where the picture was found. The server stores it on an upload too, and never fetches it. */
const FOUND_AT = "https://example.test/harbour.png"

const A_PICTURE = () => new File(["ok"], "harbour.png", { type: "image/png" })

/** The pin's image state, rebuilt rather than spread: the field is optional on the pin. */
function imageOf(pin: Pin, change: Partial<NonNullable<Pin["image"]>>): Pin["image"] {
  return { status: "READY", url: `/api/v1/pins/${pin.id}/image`, ...change }
}

/** What a PUT on the image route carried: the address it named, or the media type it was sent as. */
type Sent = string | { sourceUrl: string } | null

function recorder() {
  return { sent: [] as Sent[] }
}

/**
 * The catalogue, served from the pin as the journey holds it now, and the one route both controls
 * call. A file answers `201` with the image, an address `202` with the state it entered.
 */
function account(pin: () => Pin, record: ReturnType<typeof recorder>, refusal?: number) {
  server.use(
    sessionRoute(() => true),
    onePinPage(() => [pin()]),
    http.put("/api/v1/pins/:pinId/image", async ({ request }) => {
      const type = request.headers.get("content-type")?.split(";")[0] ?? null
      // A multipart body is read by its media type alone: the parts do not survive a jsdom
      // upload the same way on every Node the gate and a workstation run.
      record.sent.push(type === "application/json" ? ((await request.json()) as Sent) : type)
      return refusal === undefined
        ? HttpResponse.json({ status: "PENDING" }, { status: 202 })
        : new HttpResponse(null, { status: refusal })
    }),
    ...boardRoutes([]),
    downloadsRoute(),
    handshakeRoute(),
  )
}

/** The dialog opened on a tile and switched to its form. */
async function openTheForm(user: ReturnType<typeof userEvent.setup>, description: string) {
  await user.click(await screen.findByRole("img", { name: description }))
  await user.click(await screen.findByRole("button", { name: m.edit_pin() }))
  return screen.getByRole("dialog")
}

describe("replace a pin's image with a file", () => {
  it("Given a file chosen, Then one request carries it and the tile follows the new image", async () => {
    const original = readyPin("a harbour at dusk", 800, 600)
    // The route supersedes in place, so the address the tile reads is unchanged: what says the
    // new bytes arrived is the shape the layout places the tile at.
    const replaced = { ...original, image: imageOf(original, { width: 400, height: 1000 }) }
    let held: Pin = original
    const record = recorder()
    account(() => held, record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    held = replaced
    await user.upload(within(dialog).getByLabelText(m.replace_image()), A_PICTURE())

    // One request, on the route that replaces atomically: no DELETE precedes it (decision M).
    expect(record.sent).toEqual(["multipart/form-data"])
    await user.click(within(dialog).getByRole("button", { name: m.cancel() }))
    await user.click(within(dialog).getByRole("button", { name: m.close() }))
    const tile = await screen.findByRole("img", { name: original.description })
    expect(tile).toHaveStyle({ aspectRatio: "400 / 1000" })
  })

  it("Given a pin the account uploaded from disk, Then there is nothing to fetch again from", async () => {
    const held = readyPin("a harbour at dawn")
    account(() => held, recorder())
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, held.description)

    // No address, nothing to fetch: the control is absent rather than refusing when pressed.
    expect(held.sourceMediaUrl).toBeNull()
    expect(within(dialog).queryByRole("button", { name: m.fetch_image_again() })).toBeNull()
    expect(within(dialog).getByLabelText(m.replace_image())).toBeInTheDocument()
  })

  it("Given a pin carrying an address, Then the fetch sends that address and the form waits", async () => {
    const found = { ...readyPin("a harbour at noon"), sourceMediaUrl: FOUND_AT }
    // What the pin reads as while the server runs the download: the image it still has, and the
    // replacement beside it (property 8).
    const fetching = { ...found, image: imageOf(found, { replacement: { status: "PENDING" } }) }
    let held: Pin = found
    const record = recorder()
    account(() => held, record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, found.description)
    held = fetching
    await user.click(within(dialog).getByRole("button", { name: m.fetch_image_again() }))

    // The pin's own address, sent as JSON on the same route the file goes to.
    expect(record.sent).toEqual([{ sourceUrl: FOUND_AT }])
    expect(await within(dialog).findByText(m.image_replacing())).toBeVisible()
  })

  it("Given a file heavier than the deployment stores, Then no request leaves at all", async () => {
    const held = readyPin("a harbour in the rain")
    const record = recorder()
    account(() => held, record)
    server.use(handshakeRoute({ maxFileBytes: 4 }))
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, held.description)
    await user.upload(
      within(dialog).getByLabelText(m.replace_image()),
      new File(["more than four bytes"], "big.png", { type: "image/png" }),
    )

    // Judged before a byte of it is sent, and the refusal belongs to the gesture (ADR 0037).
    expect(await screen.findByRole("alert")).toHaveTextContent(m.file_too_heavy())
    expect(record.sent).toEqual([])
  })
})
