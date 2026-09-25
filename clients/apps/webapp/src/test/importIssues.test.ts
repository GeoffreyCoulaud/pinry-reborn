import { describe, expect, it } from "vitest"
import { importIssue } from "../importIssues"
import { m } from "../paraglide/messages.js"

// The API's `UserDataImportIssueKind`, entry for entry.
const KINDS = [
  "PIN_HAS_NO_MEDIA",
  "MEDIA_ENTRY_MISSING",
  "MEDIA_UNREADABLE",
  "MEDIA_TOO_LARGE",
  "MEDIA_TOO_MANY_PIXELS",
  "MEDIA_AMBIGUOUS",
  "MEDIA_DIGEST_MISMATCH",
  "LINE_MALFORMED",
  "FIELD_INVALID",
  "ENTRY_PATH_INVALID",
  "NAME_TAKEN_BY_RECYCLED",
  "LINE_REJECTED",
]

describe("the sentence an import's issue shows", () => {
  it("Given each kind the import reports, Then each has a sentence of its own", () => {
    const sentences = new Set(KINDS.map(importIssue))

    expect(sentences.size).toBe(KINDS.length)
    expect(sentences).not.toContain(m.issue_unknown())
  })

  it("Given a kind `Object.prototype` answers for, Then the user gets the general sentence", () => {
    expect(importIssue("constructor")).toBe(m.issue_unknown())
    expect(importIssue("A_KIND_FROM_A_LATER_SERVER")).toBe(m.issue_unknown())
  })
})
