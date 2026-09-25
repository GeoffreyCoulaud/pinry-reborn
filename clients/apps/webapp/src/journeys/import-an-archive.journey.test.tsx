import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { File as NodeFile } from "node:buffer"
import { afterEach, describe, expect, it, vi } from "vitest"
import { dropUpload } from "../imports"
import { RETRY_MS } from "../lib/imports"
import { m } from "../paraglide/messages.js"
import {
  downloadsRoute,
  handshakeRoute,
  IMPORT_ID,
  importRow,
  importsRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }
const ARCHIVE = "0123456789"

interface Put {
  offset: number
  type: string | null
  body: string
}

/** What the server did with the requests the section sent, in their order. */
interface Sent {
  opened: number
  puts: Put[]
  completed: number
  cancelled: string[]
}

/** The chunk appended, answered with the upload's new length. */
function appended(put: Put) {
  return HttpResponse.json(
    importRow("AWAITING_ARCHIVE", { uploadedBytes: put.offset + put.body.length }),
  )
}

function mismatch(currentLength: number) {
  return HttpResponse.json(
    { status: 409, code: "IMPORT_CHUNK_OFFSET_MISMATCH", currentLength },
    { status: 409 },
  )
}

/**
 * The account screen over one import, and a 10-byte archive cut at 4 bytes: `answer` decides
 * each chunk's fate, read from the request MSW received since `Blob.slice` reads nothing itself.
 * The first `lostCloses` closes arrive and their answers are lost on the way back.
 */
async function openTheAccount({
  rows = [] as ReturnType<typeof importRow>[],
  answer = appended as (put: Put) => Response | Promise<Response>,
  maxImportArchiveBytes = 100,
  lostCloses = 0,
} = {}) {
  const sent: Sent = { opened: 0, puts: [], completed: 0, cancelled: [] }
  let latest = rows
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
    downloadsRoute(),
    pinsRoute([]),
    handshakeRoute({ maxImportChunkBytes: 4, maxImportArchiveBytes }),
    importsRoute(() => latest),
    http.post("/api/v1/me/imports", () => {
      sent.opened += 1
      const opened = importRow("AWAITING_ARCHIVE")
      latest = [opened]
      return HttpResponse.json(opened, { status: 202 })
    }),
    http.put("/api/v1/me/imports/:id/archive", async ({ request }) => {
      const offset = Number(new URL(request.url).searchParams.get("offset"))
      const put = { offset, type: request.headers.get("Content-Type"), body: await request.text() }
      sent.puts.push(put)
      return answer(put)
    }),
    http.post("/api/v1/me/imports/:id/archive/complete", () => {
      sent.completed += 1
      // As the API does: an upload closes once, and a second close finds it no longer awaiting.
      if (latest[0]?.state !== "AWAITING_ARCHIVE") {
        return HttpResponse.json({ status: 409, code: "IMPORT_NOT_AWAITING_ARCHIVE" }, { status: 409 })
      }
      const pending = importRow("PENDING", { uploadedBytes: ARCHIVE.length })
      latest = [pending]
      if (sent.completed <= lostCloses) return HttpResponse.error()
      return HttpResponse.json(pending, { status: 202 })
    }),
    http.delete("/api/v1/me/imports/:id", ({ params }) => {
      sent.cancelled.push(String(params.id))
      latest = [importRow("CANCELLED")]
      return new HttpResponse(null, { status: 204 })
    }),
  )
  const rendered = renderApp("/account")
  // Only the timer journeys fake time, and user-event waits on timers between its steps.
  const user = userEvent.setup({
    advanceTimers: (ms) => (vi.isFakeTimers() ? vi.advanceTimersByTime(ms) : undefined),
  })
  await screen.findByRole("heading", { name: m.import_heading() })
  return { user, sent, router: rendered.router }
}

/** These answers in their order, then every chunk appended. */
function answering(...answers: ((put: Put) => Response)[]) {
  return (put: Put) => (answers.shift() ?? appended)(put)
}

async function chooseTheArchive(user: ReturnType<typeof userEvent.setup>) {
  const picker = await screen.findByLabelText(m.import_choose())
  // Disabled until the handshake says how large a chunk is.
  await waitFor(() => expect(picker).toBeEnabled())
  // Node's own `File`: the fetch under test sends jsdom's slices as the text "undefined".
  const archive = new NodeFile([ARCHIVE], "pinry.zip", { type: "application/zip" })
  await user.upload(picker, archive as File)
}

/** Past the wait before each retry, whenever the loop gets round to scheduling it. */
async function afterRetries(check: () => void) {
  await vi.waitFor(async () => {
    await vi.advanceTimersByTimeAsync(RETRY_MS)
    check()
  })
}

/** The page asks before closing when a listener cancels `beforeunload`. */
function pageHeldOnUnload() {
  const event = new Event("beforeunload", { cancelable: true })
  window.dispatchEvent(event)
  return event.defaultPrevented
}

/** Whether `text` showed at any moment, which a `findBy` on the last screen cannot tell. */
function watchFor(text: string) {
  let shown = false
  const observer = new MutationObserver(() => {
    shown ||= document.body.textContent?.includes(text) ?? false
  })
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })
  return () => {
    observer.disconnect()
    return shown
  }
}

afterEach(() => {
  // The store is module state, and module state outlives a journey.
  dropUpload()
  vi.useRealTimers()
})

describe("import an archive", () => {
  it("Given a 4-byte chunk, Then the archive leaves in three slices and the upload closes", async () => {
    const { user, sent } = await openTheAccount()
    const shownStopped = watchFor(m.import_awaiting({ name: "pinry.zip" }))

    await chooseTheArchive(user)

    expect(await screen.findByText(m.import_pending())).toBeVisible()
    expect(sent.puts).toEqual([
      { offset: 0, type: "application/octet-stream", body: "0123" },
      { offset: 4, type: "application/octet-stream", body: "4567" },
      { offset: 8, type: "application/octet-stream", body: "89" },
    ])
    expect(sent.completed).toBe(1)
    // The upload gives way to the server's row once it is read, not to the stale one before it.
    expect(shownStopped()).toBe(false)
  })

  it("Given a close whose answer is lost, Then it is sent again and the server's import shows", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { user, sent } = await openTheAccount({ lostCloses: 1 })

    await chooseTheArchive(user)
    await afterRetries(() => expect(sent.completed).toBe(2))

    expect(await screen.findByText(m.import_pending())).toBeVisible()
    expect(screen.queryByRole("progressbar", { name: m.import_sending() })).toBeNull()
    expect(screen.queryByText(m.account_refused())).toBeNull()
    expect(sent.puts).toHaveLength(3)
  })

  it("Given a lost request and a cut chunk, Then the upload resumes where the server stands", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    // The chunk at 4 is cut after two bytes: the file on the server is the truth.
    const answer = answering(() => HttpResponse.error(), appended, () => mismatch(6))
    const { user, sent } = await openTheAccount({ answer })

    await chooseTheArchive(user)
    await afterRetries(() => expect(sent.puts.length).toBeGreaterThanOrEqual(2))

    expect(await screen.findByText(m.import_pending())).toBeVisible()
    expect(sent.puts.map((put) => put.offset)).toEqual([0, 0, 4, 6])
    expect(sent.puts[3]?.body).toBe("6789")
    expect(sent.completed).toBe(1)
  })

  it("Given a server holding more than the file, Then the upload stops and nothing closes it", async () => {
    const { user, sent } = await openTheAccount({ answer: answering(appended, () => mismatch(12)) })

    await chooseTheArchive(user)

    expect(await screen.findByText(m.import_other_file())).toBeVisible()
    expect(sent.puts).toHaveLength(2)
    expect(sent.completed).toBe(0)
    expect(pageHeldOnUnload()).toBe(false)
  })

  it("Given five lost requests in a row, Then the upload pauses until the user resumes it", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let lost = 5
    const { user, sent } = await openTheAccount({
      answer: (put) => (lost-- > 0 ? HttpResponse.error() : appended(put)),
    })

    await chooseTheArchive(user)
    await afterRetries(() => expect(sent.puts.length).toBeGreaterThanOrEqual(5))

    const resume = await screen.findByRole("button", { name: m.import_resume() })
    expect(screen.getByText(m.import_paused())).toBeVisible()
    expect(sent.puts).toHaveLength(5)

    await user.click(resume)

    expect(await screen.findByText(m.import_pending())).toBeVisible()
    expect(sent.puts.map((put) => put.offset)).toEqual([0, 0, 0, 0, 0, 0, 4, 8])
  })

  it("Given a file past the deployment's bound, Then it is refused and nothing is sent", async () => {
    const { user, sent } = await openTheAccount({ maxImportArchiveBytes: 9 })

    await chooseTheArchive(user)

    expect(await screen.findByText(m.import_too_large())).toBeVisible()
    expect(sent).toEqual({ opened: 0, puts: [], completed: 0, cancelled: [] })
  })

  it("Given an import already running, Then opening another is refused and nothing is sent", async () => {
    const { user, sent } = await openTheAccount()
    const running = { status: 409, code: "IMPORT_ALREADY_IN_PROGRESS" }
    server.use(http.post("/api/v1/me/imports", () => HttpResponse.json(running, { status: 409 })))

    await chooseTheArchive(user)

    expect(await screen.findByText(m.import_in_progress())).toBeVisible()
    expect(sent.puts).toEqual([])
  })

  it("Given an upload, Then it outlives the screen and holds the page until it ends", async () => {
    const gates = new Map<number, () => void>()
    const { user, sent, router } = await openTheAccount({
      answer: async (put) => {
        if (put.offset > 0) await new Promise<void>((open) => gates.set(put.offset, open))
        return appended(put)
      },
    })
    expect(pageHeldOnUnload()).toBe(false)

    await chooseTheArchive(user)
    await waitFor(() => expect(gates.has(4)).toBe(true))
    expect(pageHeldOnUnload()).toBe(true)

    await router.navigate({ to: "/" })
    await waitFor(() => expect(screen.queryByText(m.import_heading())).toBeNull())
    gates.get(4)?.()
    // The chunk at 8 leaves while the account screen is not mounted at all.
    await waitFor(() => expect(gates.has(8)).toBe(true))
    await router.navigate({ to: "/account" })

    const progress = await screen.findByRole("progressbar", { name: m.import_sending() })
    expect(progress).toHaveAttribute("aria-valuenow", "8")

    gates.get(8)?.()

    expect(await screen.findByText(m.import_pending())).toBeVisible()
    expect(sent.completed).toBe(1)
    expect(pageHeldOnUnload()).toBe(false)
  })

  it("Given a running import, Then cancelling asks first and then sends the DELETE", async () => {
    const running = importRow("RUNNING", { announcedPins: 10, processedPins: 3 })
    const { user, sent } = await openTheAccount({ rows: [running] })

    expect(await screen.findByText("Importing: 3 of 10 pins.")).toBeVisible()
    await user.click(screen.getByRole("button", { name: m.import_cancel() }))

    expect(await screen.findByText(m.import_cancel_warning())).toBeVisible()
    expect(sent.cancelled).toEqual([])

    await user.click(screen.getByRole("button", { name: m.import_cancel_confirm() }))

    await waitFor(() => expect(screen.queryByText("Importing: 3 of 10 pins.")).toBeNull())
    expect(sent.cancelled).toEqual([IMPORT_ID])
  })

  it("Given a cancel refused during an upload, Then it says so and the upload goes on", async () => {
    const gates = new Map<number, () => void>()
    const { user, sent } = await openTheAccount({
      answer: async (put) => {
        if (put.offset > 0) await new Promise<void>((open) => gates.set(put.offset, open))
        return appended(put)
      },
    })
    server.use(http.delete("/api/v1/me/imports/:id", () => new HttpResponse(null, { status: 500 })))
    await chooseTheArchive(user)
    await waitFor(() => expect(gates.has(4)).toBe(true))

    await user.click(screen.getByRole("button", { name: m.import_cancel() }))
    await user.click(await screen.findByRole("button", { name: m.import_cancel_confirm() }))

    expect(await screen.findByText(m.account_refused())).toBeVisible()
    gates.get(4)?.()
    await waitFor(() => expect(gates.has(8)).toBe(true))
    expect(screen.getByRole("progressbar", { name: m.import_sending() })).toBeVisible()
    expect(sent.cancelled).toEqual([])
  })
})
