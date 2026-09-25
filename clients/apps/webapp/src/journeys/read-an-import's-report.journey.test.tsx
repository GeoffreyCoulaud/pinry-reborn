import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { HttpResponse, http } from "msw"
import { describe, expect, it } from "vitest"
import { m } from "../paraglide/messages.js"
import {
  downloadsRoute,
  handshakeRoute,
  importRow,
  importsRoute,
  pinsRoute,
  renderApp,
  sessionRoute,
} from "../test/app"
import { server } from "../test/server"

const ACCOUNT = { id: "0f5c6e58-2d6c-4a3a-9c1f-2a1f6b6d4f11", name: "ada" }

let issueCount = 0

/** An issue as `GET .../issues` answers one. */
function issue(kind: string, line: number | null, subject: string | null) {
  const id = `9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c${(issueCount++).toString().padStart(2, "0")}`
  return { id, kind, line, subject, detail: "the server's own words" }
}

// The cursor is opaque: the second page is named by what the first one answered, nothing else.
const PAGES: Record<string, ReturnType<typeof issue>[]> = {
  first: [issue("MEDIA_ENTRY_MISSING", 3, "media/a.png"), issue("FIELD_INVALID", 5, "Recipes")],
  "after-5": [issue("A_KIND_FROM_A_LATER_SERVER", null, null)],
}

const COMPLETED = importRow("COMPLETED", {
  startedAt: "2026-09-23T12:05:00Z",
  completedAt: "2026-09-23T12:10:00Z",
  processedPins: 15,
  createdPins: 12,
  skippedPins: 3,
  createdBoards: 2,
  skippedBoards: 1,
  createdTags: 5,
  skippedTags: 0,
  issueCount: 3,
})

/** The account screen over one import, and the cursors its report was read with. */
function openTheAccount(row: unknown) {
  const cursors: (string | null)[] = []
  server.use(
    sessionRoute(() => true),
    http.get("/api/v1/me", () => HttpResponse.json(ACCOUNT)),
    downloadsRoute(),
    pinsRoute([]),
    handshakeRoute(),
    importsRoute(() => [row]),
    http.get("/api/v1/me/imports/:id/issues", ({ request }) => {
      const cursor = new URL(request.url).searchParams.get("cursor")
      cursors.push(cursor)
      const issues = PAGES[cursor ?? "first"] ?? []
      const nextCursor = cursor === null ? "after-5" : null
      return HttpResponse.json({ issues, pagination: { previousCursor: null, nextCursor } })
    }),
  )
  renderApp("/account")
  return { user: userEvent.setup(), cursors }
}

/** The report's dialog, opened from the section's button. */
async function openTheReport(row: unknown) {
  const opened = openTheAccount(row)
  await opened.user.click(
    await screen.findByRole("button", { name: m.import_issues_open({ count: "3" }) }),
  )
  return { ...opened, dialog: await screen.findByRole("dialog") }
}

describe("read an import's report", () => {
  it("Given a completed import, Then the section shows its counters", async () => {
    openTheAccount(COMPLETED)

    const pins = m.import_counted_pins({ created: "12", skipped: "3" })
    expect(await screen.findByText(pins)).toBeVisible()
    expect(screen.getByText(m.import_counted_boards({ created: "2", skipped: "1" }))).toBeVisible()
    expect(screen.getByText(m.import_counted_tags({ created: "5", skipped: "0" }))).toBeVisible()
  })

  it("Given the report opened, Then load more sends the answered cursor and appends", async () => {
    const { user, cursors, dialog } = await openTheReport(COMPLETED)

    const first = within(dialog)
    expect(await first.findByText(m.issue_media_entry_missing())).toBeVisible()
    expect(first.getByText(m.import_issue_line({ line: "3" }))).toBeVisible()
    expect(first.getByText("media/a.png")).toBeVisible()
    expect(first.getByText(m.issue_field_invalid())).toBeVisible()

    await user.click(first.getByRole("button", { name: m.import_issues_more() }))

    expect(await first.findByText(m.issue_unknown())).toBeVisible()
    expect(first.getByText(m.issue_media_entry_missing())).toBeVisible()
    expect(cursors).toEqual([null, "after-5"])
    expect(first.queryByRole("button", { name: m.import_issues_more() })).toBeNull()
    expect(dialog).not.toHaveTextContent("undefined")
    expect(first.queryByText(m.import_issues_truncated({ count: "3" }))).toBeNull()
  })

  it("Given a report past its detail limit, Then the dialog says only the first are listed", async () => {
    const { dialog } = await openTheReport({ ...COMPLETED, issueDetailTruncated: true })

    expect(await within(dialog).findByText(m.import_issues_truncated({ count: "3" }))).toBeVisible()
  })

  it("Given a failed import, Then the section says why, or the general sentence", async () => {
    openTheAccount(importRow("FAILED", { failureCode: "ARCHIVE_UNREADABLE" }))
    expect(await screen.findByText(m.failure_archive_unreadable())).toBeVisible()
  })

  it("Given a failure code this bundle does not know, Then the section gives the general sentence", async () => {
    openTheAccount(importRow("FAILED", { failureCode: "A_CODE_FROM_A_LATER_SERVER" }))
    expect(await screen.findByText(m.failure_unknown())).toBeVisible()
  })
})
