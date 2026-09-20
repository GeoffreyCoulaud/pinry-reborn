import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import type { Pin } from "../pins"
import {
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

function recorder() {
  return { pages: 0, deleted: [] as unknown[], restored: [] as unknown[] }
}

/**
 * The catalogue, the bin, and the two writes between them. The catalogue always answers both
 * pins, so a tile that has left the grid can only have left the cached pages: a refetch would
 * put it back, and the page count says whether one happened.
 */
function account(pins: Pin[], record: ReturnType<typeof recorder>, refusal?: number) {
  const bin: Pin[] = []
  server.use(
    sessionRoute(() => true),
    pinsRoute([pins], () => {
      record.pages += 1
    }),
    http.delete("/api/v1/pins", async ({ request }) => {
      const body = (await request.json()) as { pinIds: string[] }
      if (refusal !== undefined) return new HttpResponse(null, { status: refusal })
      record.deleted.push(body)
      bin.push(...pins.filter((pin) => body.pinIds.includes(pin.id)))
      return new HttpResponse(null, { status: 204 })
    }),
    http.get("/api/v1/pins/recycled", () =>
      HttpResponse.json({ pins: bin, pagination: { previousCursor: null, nextCursor: null } }),
    ),
    http.post("/api/v1/pins/recycled/restore", async ({ request }) => {
      record.restored.push(await request.json())
      bin.length = 0
      return new HttpResponse(null, { status: 204 })
    }),
    downloadsRoute(),
    handshakeRoute(),
  )
}

/** The dialog a tile opens, where the delete lives (specification 2026-09-20, decision K). */
async function openThePin(user: ReturnType<typeof userEvent.setup>, description: string) {
  await user.click(await screen.findByRole("img", { name: description }))
  return screen.getByRole("dialog")
}

describe("delete a pin and restore it from the recycle bin", () => {
  it("Given the pin deleted, Then the tile leaves the grid, stands in the bin, and comes back", async () => {
    const [gone, kept] = [readyPin("a harbour at dusk"), readyPin("a harbour at dawn")]
    const record = recorder()
    account([gone, kept], record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openThePin(user, gone.description)
    await user.click(within(dialog).getByRole("button", { name: m.delete_pin() }))

    // One request, in bulk: a pin is a list of one, which is the shape block 90 sends too.
    expect(record.deleted).toEqual([{ pinIds: [gone.id] }])
    expect(await screen.findByRole("img", { name: kept.description })).toBeVisible()
    expect(screen.queryByRole("img", { name: gone.description })).toBeNull()
    // The discriminating half: the tiles leave the cached pages, never a refetch (decision P).
    expect(record.pages).toBe(1)

    await user.click(screen.getByRole("link", { name: m.recycle_bin() }))
    const row = await screen.findByRole("row", { name: gone.description })
    await user.click(within(row).getByRole("button", { name: m.restore({ name: gone.description }) }))

    expect(record.restored).toEqual([{ pinIds: [gone.id] }])
    expect(await screen.findByRole("status")).toHaveTextContent(m.bin_empty())
  })

  it("Given the API refuses the delete, Then the tile stays and the refusal is said", async () => {
    const pin = readyPin("a harbour at dusk")
    const record = recorder()
    account([pin], record, 404)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openThePin(user, pin.description)
    await user.click(within(dialog).getByRole("button", { name: m.delete_pin() }))

    expect(await screen.findByText(m.pin_deletion_refused())).toBeVisible()
    expect(screen.getByRole("img", { name: pin.description })).toBeVisible()
  })
})
