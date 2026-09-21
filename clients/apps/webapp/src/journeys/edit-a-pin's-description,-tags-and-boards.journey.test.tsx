import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import type { Pin } from "../pins"
import {
  board,
  boardPinsRoute,
  boardRoutes,
  downloadsRoute,
  handshakeRoute,
  pinsRoute,
  readyPin,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const EVENINGS = board("Evenings")

/** What the account holds before the edit: one tag, no board. */
function held(): Pin {
  return { ...readyPin("a harbour at dusk"), tags: [{ name: "harbours" }], boards: [] }
}

/**
 * The catalogue, the write, the reread and the tag search. The page is served from the pin as it
 * was read, so a tile carrying the new description can only have been rewritten in place.
 */
function account(
  original: Pin,
  saved: Pin,
  record: { pages: number; bodies: unknown[]; queries: (string | null)[] },
  refusal?: number,
) {
  server.use(
    sessionRoute(() => true),
    pinsRoute([[original]], () => {
      record.pages += 1
    }),
    http.put("/api/v1/pins/:pinId", async ({ request }) => {
      record.bodies.push(await request.json())
      return refusal === undefined
        ? HttpResponse.json(saved)
        : new HttpResponse(null, { status: refusal })
    }),
    http.get("/api/v1/pins/:pinId", () => HttpResponse.json(saved)),
    http.get("/api/v1/tags/search", ({ request }) => {
      record.queries.push(new URL(request.url).searchParams.get("q"))
      return HttpResponse.json({ results: [{ tag: { name: "landscape" }, score: 1 }] })
    }),
    ...boardRoutes([EVENINGS]),
    downloadsRoute(),
    handshakeRoute(),
  )
}

function recorder() {
  return { pages: 0, bodies: [] as unknown[], queries: [] as (string | null)[] }
}

/** The dialog opened on a tile and switched to its form. */
async function openTheForm(user: ReturnType<typeof userEvent.setup>, description: string) {
  await user.click(await screen.findByRole("img", { name: description }))
  await user.click(await screen.findByRole("button", { name: m.edit_pin() }))
  return screen.getByRole("dialog")
}

describe("edit a pin's description, tags and boards", () => {
  it("Given the form, Then one request carries the whole pin and the tile follows it", async () => {
    const original = held()
    const saved = {
      ...original,
      description: "a harbour at dawn",
      tags: [{ name: "harbours" }, { name: "landscape" }],
      boards: [{ id: EVENINGS.id, name: EVENINGS.name }],
    }
    const record = recorder()
    account(original, saved, record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    const description = within(dialog).getByRole("textbox", { name: m.description() })
    await user.clear(description)
    await user.type(description, saved.description)
    await user.type(within(dialog).getByRole("textbox", { name: m.tags() }), "Landscape")
    await user.click(await screen.findByRole("button", { name: "landscape" }))
    await user.click(within(dialog).getByRole("button", { name: new RegExp(m.boards()) }))
    await user.click(await screen.findByRole("option", { name: EVENINGS.name }))
    // A multiple select stays open on a pick, and its popover hides the form behind it.
    await user.keyboard("{Escape}")
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    // One request, not three: the route replaces the pin, so what is unchanged is sent as read.
    expect(record.bodies).toEqual([
      {
        description: saved.description,
        sourceContextUrl: original.sourceContextUrl,
        sourceMediaUrl: null,
        tags: ["harbours", "landscape"],
        boardIds: [EVENINGS.id],
      },
    ])
    // The dialog is back to reading, and the tile carries the new description.
    expect(await within(dialog).findByText(saved.description)).toBeVisible()
    expect(screen.getByRole("img", { name: saved.description })).toBeVisible()
    // The discriminating half: the catalogue is written through, never refetched (specification
    // 2026-09-20, decision P).
    expect(record.pages).toBe(1)
    // The field pauses, so a term typed in one go is asked for once and not once per character
    // (specification 2026-09-21, decision P).
    expect(record.queries).toEqual(["Landscape"])
  })

  it("Given a name the account holds under another case, Then the tag it holds is offered", async () => {
    const original = held()
    const record = recorder()
    account(original, original, record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    await user.type(within(dialog).getByRole("textbox", { name: m.tags() }), "Landscape")

    // The server folds to ASCII for identity, so the field asks it before inventing a tag.
    expect(await screen.findByRole("button", { name: "landscape" })).toBeVisible()
    expect(record.queries.at(-1)).toBe("Landscape")
  })

  it("Given the last tag and board taken off, Then the request carries the empty lists", async () => {
    const original = { ...held(), boards: [{ id: EVENINGS.id, name: EVENINGS.name }] }
    const record = recorder()
    account(original, { ...original, tags: [], boards: [] }, record)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    await user.click(within(dialog).getByRole("button", { name: /harbours/ }))
    await user.click(within(dialog).getByRole("button", { name: new RegExp(EVENINGS.name) }))
    await user.click(await screen.findByRole("option", { name: EVENINGS.name }))
    await user.keyboard("{Escape}")
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    // An empty list clears rather than leaving alone: there is one way to say "unchanged".
    expect(record.bodies).toEqual([
      expect.objectContaining({ tags: [], boardIds: [] }),
    ])
  })

  it("Given the edit takes the pin out of the board it is read on, Then its tile leaves that grid", async () => {
    const original = { ...held(), boards: [{ id: EVENINGS.id, name: EVENINGS.name }] }
    const kept = { ...readyPin("a cat asleep"), boards: [{ id: EVENINGS.id, name: EVENINGS.name }] }
    const record = recorder()
    account(original, { ...original, boards: [] }, record)
    server.use(boardPinsRoute({ [EVENINGS.id]: [[original, kept]] }))
    renderApp(`/boards/${EVENINGS.id}`)
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    await user.click(within(dialog).getByRole("button", { name: new RegExp(EVENINGS.name) }))
    await user.click(await screen.findByRole("option", { name: EVENINGS.name }))
    await user.keyboard("{Escape}")
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    // The tile leaves the grid it no longer belongs to rather than standing there until a reload,
    // which is what the same outcome reached through the selection bar already does. The pin the
    // edit did not touch stays, so what left is the one that left the board. Read with `hidden`:
    // the dialog is still over the grid on a save that changes no membership.
    const grid = await screen.findByRole("grid", { name: EVENINGS.name, hidden: true })
    const tile = (name: string) => within(grid).queryByRole("img", { name, hidden: true })
    await waitFor(() => expect(tile(original.description)).toBeNull())
    expect(tile(kept.description)).toBeInTheDocument()
  })

  it("Given the API refuses the write, Then the form stays open and says so", async () => {
    const original = held()
    const record = recorder()
    account(original, original, record, 404)
    renderApp("/")
    const user = userEvent.setup()

    const dialog = await openTheForm(user, original.description)
    await user.click(within(dialog).getByRole("button", { name: m.save() }))

    expect(await within(dialog).findByRole("alert")).toHaveTextContent(m.pin_refused())
    expect(within(dialog).getByRole("button", { name: m.save() })).toBeVisible()
  })
})
