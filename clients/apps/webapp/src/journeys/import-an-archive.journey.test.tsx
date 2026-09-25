import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  downloadsRoute,
  exportsRoute,
  handshakeRoute,
  importsRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }
const IMPORT_ID = "3b9d2c1e-4f5a-4b6c-8d7e-9f0a1b2c3d4e"

/** An import row as the API answers one, in the state the journey names. */
function importRow(state: string, fields: Record<string, unknown> = {}) {
  return {
    id: IMPORT_ID,
    state,
    requestedAt: "2026-09-23T12:00:00Z",
    uploadedBytes: 0,
    byteSize: null,
    archiveCompletedAt: null,
    startedAt: null,
    completedAt: null,
    formatVersion: null,
    announcedPins: null,
    processedPins: 0,
    createdPins: 0,
    skippedPins: 0,
    createdBoards: 0,
    skippedBoards: 0,
    createdTags: 0,
    skippedTags: 0,
    issueCount: 0,
    issueDetailTruncated: false,
    failureCode: null,
    ...fields,
  }
}

/** What the server did with the requests the section sent, in their order. */
interface Sent {
  cancelled: string[]
}

/** The account screen over one import, whose row the journey's requests change. */
async function openTheAccount({ rows = [] as unknown[] } = {}) {
  const sent: Sent = { cancelled: [] }
  let latest = rows
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
    downloadsRoute(),
    exportsRoute(),
    pinsRoute([]),
    handshakeRoute(),
    importsRoute(() => latest),
    http.delete("/api/v1/me/imports/:id", ({ params }) => {
      sent.cancelled.push(String(params.id))
      latest = [importRow("CANCELLED")]
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderApp("/account")
  const user = userEvent.setup()
  await screen.findByRole("heading", { name: m.import_heading() })
  return { user, sent }
}

describe("import an archive", () => {
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
})
