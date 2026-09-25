import { cleanup, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { File as NodeFile } from "node:buffer"
import { afterEach, beforeEach, describe, expect, it } from "vitest"
import { dropUpload } from "../imports"
import { m } from "../paraglide/messages.js"
import {
  downloadsRoute,
  exportsRoute,
  handshakeRoute,
  importRow,
  importsRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }
const ARCHIVE = "0123456789"
const LAST_MODIFIED = 1_758_800_000_000

// Node's own `File`: the fetch under test sends jsdom's slices as the text "undefined".
const archive = (content = ARCHIVE) =>
  new NodeFile([content], "pinry.zip", { type: "application/zip", lastModified: LAST_MODIFIED })

interface Put {
  offset: number
  body: string
}

/**
 * The account screen over `rows`, and a 4-byte chunk: every chunk is appended, except the one at
 * 4 on the first page, which waits for the reload that cuts it.
 */
function openTheAccount(rows: unknown[] = []) {
  const puts: Put[] = []
  let latest = rows
  let reloaded = false
  /** The row the server now holds, which the next read of the latest import answers. */
  const holding = (row: ReturnType<typeof importRow>, status = 200) => {
    latest = [row]
    return HttpResponse.json(row, { status })
  }
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
    downloadsRoute(),
    exportsRoute(),
    pinsRoute([]),
    handshakeRoute({ maxImportChunkBytes: 4 }),
    importsRoute(() => latest),
    http.post("/api/v1/me/imports", () => holding(importRow("AWAITING_ARCHIVE"), 202)),
    http.put("/api/v1/me/imports/:id/archive", async ({ request }) => {
      const offset = Number(new URL(request.url).searchParams.get("offset"))
      const put = { offset, body: await request.text() }
      puts.push(put)
      if (offset === 4 && !reloaded) return new Promise<Response>(() => {})
      return holding(importRow("AWAITING_ARCHIVE", { uploadedBytes: offset + put.body.length }))
    }),
    http.post("/api/v1/me/imports/:id/archive/complete", () =>
      holding(importRow("PENDING", { uploadedBytes: ARCHIVE.length }), 202),
    ),
  )
  renderApp("/account")
  return {
    user: userEvent.setup(),
    puts,
    /** A fresh application: the tab's upload gone, the server and `localStorage` as they were. */
    reload(next?: unknown[]) {
      reloaded = true
      latest = next ?? latest
      dropUpload()
      cleanup()
      renderApp("/account")
    },
  }
}

/** An archive chosen on the account screen, whose chunk at 4 the reload cuts. */
async function startThenReload(next?: unknown[]) {
  const opened = openTheAccount()
  await opened.user.upload(await enabled(m.import_choose()), archive() as File)
  await waitFor(() => expect(opened.puts.map((put) => put.offset)).toEqual([0, 4]))
  expect(localStorage.length).toBe(1)
  opened.reload(next)
  return opened
}

/** Disabled until the handshake says how large a chunk is. */
async function enabled(label: string) {
  const picker = await screen.findByLabelText(label)
  await waitFor(() => expect(picker).toBeEnabled())
  return picker
}

beforeEach(() => localStorage.clear())

afterEach(() => {
  // The store is module state, and module state outlives a journey (specification, section 9).
  dropUpload()
})

describe("resume an import after reloading", () => {
  it("Given the same file chosen again, Then the upload resumes where the server stands", async () => {
    const { user, puts } = await startThenReload()

    const picker = await enabled(m.import_choose_again())
    const sent = screen.getByRole("progressbar", { name: m.import_sent() })
    expect(sent).toHaveAttribute("aria-valuenow", "4")
    expect(sent).toHaveAttribute("aria-valuemax", String(ARCHIVE.length))

    await user.upload(picker, archive() as File)

    expect(await screen.findByText(m.import_pending())).toBeVisible()
    expect(puts.slice(2)).toEqual([
      { offset: 4, body: "4567" },
      { offset: 8, body: "89" },
    ])
    // The record served the import while it waited on its archive, and it waits no more.
    await waitFor(() => expect(localStorage.length).toBe(0))
  })

  it("Given a file of another size, Then it is refused and nothing is sent", async () => {
    const { user, puts } = await startThenReload()

    await user.upload(await enabled(m.import_choose_again()), archive("0123456789A") as File)

    expect(await screen.findByText(m.import_not_the_file({ name: "pinry.zip" }))).toBeVisible()
    expect(puts).toHaveLength(2)
  })

  it("Given no record, Then the section offers the cancel button and no file picker", async () => {
    openTheAccount([importRow("AWAITING_ARCHIVE", { uploadedBytes: 4 })])

    expect(await screen.findByText(m.import_elsewhere())).toBeVisible()
    expect(screen.getByRole("button", { name: m.import_cancel() })).toBeVisible()
    expect(screen.queryByLabelText(m.import_choose_again())).toBeNull()
    expect(screen.queryByLabelText(m.import_choose())).toBeNull()
  })

  it("Given another import as the latest, Then the older one's record goes on the first read", async () => {
    const other = importRow("AWAITING_ARCHIVE", { id: "7c1e2d3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f" })

    await startThenReload([other])

    expect(await screen.findByText(m.import_elsewhere())).toBeVisible()
    expect(localStorage.length).toBe(0)
  })
})
