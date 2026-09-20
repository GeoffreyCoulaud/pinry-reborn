import { screen, waitFor, within } from "@testing-library/react"
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

/** What each write carried, in the order the two arrived: the pin's body, then the image's. */
type Written =
  | { pin: { sourceMediaUrl: string | null } }
  | { image: string | { sourceUrl: string } | null }

function recorder() {
  return { written: [] as Written[] }
}

/**
 * The catalogue, served from the pin as the journey holds it now, the write of the pin, and the
 * one route every image option calls. A file answers `201`, an address `202`.
 */
function account(
  pin: () => Pin,
  record: ReturnType<typeof recorder>,
  { reread = pin, imageStatus = 202 }: { reread?: () => Pin; imageStatus?: number } = {},
) {
  server.use(
    sessionRoute(() => true),
    onePinPage(() => [pin()]),
    http.put("/api/v1/pins/:pinId", async ({ request }) => {
      record.written.push({ pin: (await request.json()) as { sourceMediaUrl: string | null } })
      return HttpResponse.json(reread())
    }),
    // What the write rereads into the pages the grid holds (decision P), served as the pin was
    // read: a tile that changes shape can then only have followed the image write.
    http.get("/api/v1/pins/:pinId", () => HttpResponse.json(reread())),
    http.put("/api/v1/pins/:pinId/image", async ({ request }) => {
      const type = request.headers.get("content-type")?.split(";")[0] ?? null
      // A multipart body is read by its media type alone: the parts do not survive a jsdom
      // upload the same way on every Node the gate and a workstation run.
      const body = type === "application/json" ? await request.json() : type
      record.written.push({ image: body as { sourceUrl: string } | string | null })
      return imageStatus < 300
        ? HttpResponse.json({ status: "PENDING" }, { status: imageStatus })
        : new HttpResponse(null, { status: imageStatus })
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
  it("Given the file option and a file, Then the save applies it and the tile follows", async () => {
    const original = readyPin("a harbour at dusk", 800, 600)
    // The route supersedes in place, so the address the tile reads is unchanged: what says the
    // new bytes arrived is the shape the layout places the tile at.
    const replaced = { ...original, image: imageOf(original, { width: 400, height: 1000 }) }
    let held: Pin = original
    const record = recorder()
    account(() => held, record, { reread: () => original })
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    // The image the form is editing, above the choice of what to do with it.
    expect(within(dialog).getByRole("img", { name: original.description })).toBeVisible()
    await user.click(within(dialog).getByRole("radio", { name: m.image_replace() }))
    await user.upload(within(dialog).getByLabelText(m.drop_image()), A_PICTURE())
    // Chosen and not sent: nothing reaches the server until the pin is saved.
    expect(await within(dialog).findByText("harbour.png")).toBeVisible()
    expect(record.written).toEqual([])

    held = replaced
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    // The pin first, then the option chosen, on the route that supersedes atomically: no DELETE
    // precedes it (decision M).
    await waitFor(() => expect(record.written).toHaveLength(2))
    expect(record.written[1]).toEqual({ image: "multipart/form-data" })

    // The dialog is back to reading, and the grid behind it carries the image that just landed.
    await user.click(await within(dialog).findByRole("button", { name: m.close() }))
    const tile = await screen.findByRole("img", { name: original.description })
    expect(tile).toHaveStyle({ aspectRatio: "400 / 1000" })
  })

  it("Given an address typed where the pin had none, Then the fetch is offered and applied", async () => {
    const found = readyPin("a harbour at dawn")
    // What the pin reads as while the server runs the download: the image it still has, and the
    // replacement beside it (property 8).
    const fetching = { ...found, image: imageOf(found, { replacement: { status: "PENDING" } }) }
    let held: Pin = found
    const record = recorder()
    account(() => held, record, { reread: () => fetching })
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, found.description)

    // No address, nothing to fetch: the option is absent rather than refusing when chosen.
    expect(found.sourceMediaUrl).toBeNull()
    expect(within(dialog).queryByRole("radio", { name: m.fetch_image_from_url() })).toBeNull()

    await user.type(within(dialog).getByRole("textbox", { name: m.image_address() }), FOUND_AT)
    // The option follows the field, not the pin as it was read, so no save and no second edit.
    await user.click(await within(dialog).findByRole("radio", { name: m.fetch_image_from_url() }))
    held = fetching
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    // The pin is written first, so the address the fetch reads is the one the pin now holds.
    await waitFor(() => expect(record.written).toHaveLength(2))
    expect(record.written).toEqual([
      { pin: expect.objectContaining({ sourceMediaUrl: FOUND_AT }) },
      { image: { sourceUrl: FOUND_AT } },
    ])
    // The form closes on the save, so the sub-state is what the next edit of that pin reads.
    await user.click(await within(dialog).findByRole("button", { name: m.edit_pin() }))
    expect(await within(dialog).findByText(m.image_replacing())).toBeVisible()
  })

  it("Given the image kept, Then the save writes the pin and touches no image", async () => {
    const held = { ...readyPin("a harbour at noon"), sourceMediaUrl: FOUND_AT }
    const record = recorder()
    account(() => held, record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, held.description)
    // Keeping is the default, and the address the pin already carries offers the fetch beside it.
    expect(within(dialog).getByRole("radio", { name: m.image_keep() })).toBeChecked()
    expect(within(dialog).getByRole("radio", { name: m.fetch_image_from_url() })).toBeVisible()
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    await waitFor(() => expect(record.written).toHaveLength(1))
    expect(record.written[0]).toEqual({ pin: expect.objectContaining({ sourceMediaUrl: FOUND_AT }) })
  })

  it("Given the image refused after the pin was written, Then the form says which half failed", async () => {
    const held = { ...readyPin("a harbour in the fog"), sourceMediaUrl: FOUND_AT }
    const record = recorder()
    account(() => held, record, { imageStatus: 500 })
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, held.description)
    await user.click(within(dialog).getByRole("radio", { name: m.fetch_image_from_url() }))
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    // The fields are saved and the image is not, so the form says that and not that the save
    // failed whole.
    expect(await within(dialog).findByText(m.image_refused())).toBeVisible()
    expect(within(dialog).queryByText(m.pin_refused())).toBeNull()
    expect(within(dialog).getByRole("button", { name: m.save() })).toBeVisible()
  })

  it("Given a file heavier than the deployment stores, Then it is never chosen at all", async () => {
    const held = readyPin("a harbour in the rain")
    const record = recorder()
    account(() => held, record)
    server.use(handshakeRoute({ maxFileBytes: 4 }))
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, held.description)
    await user.click(within(dialog).getByRole("radio", { name: m.image_replace() }))
    await user.upload(
      within(dialog).getByLabelText(m.drop_image()),
      new File(["more than four bytes"], "big.png", { type: "image/png" }),
    )

    // Judged at the choice and not at the save, and the refusal belongs to the gesture (ADR 0037).
    expect(await screen.findByRole("alert")).toHaveTextContent(m.file_too_heavy())
    expect(within(dialog).queryByText("big.png")).toBeNull()
    // Nothing to replace with, so the save that would silently keep the image is refused.
    expect(within(dialog).getByRole("button", { name: m.save() })).toBeDisabled()
  })
})
