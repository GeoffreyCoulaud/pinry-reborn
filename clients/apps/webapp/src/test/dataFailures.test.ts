import { describe, expect, it } from "vitest"
import { dataFailure } from "../dataFailures"
import { m } from "../paraglide/messages.js"

describe("the sentence a failed export or import shows", () => {
  it("Given each code the export writes, Then none falls to the general sentence", () => {
    for (const code of ["DISK_FULL", "BUILD_FAILED", "EXPORT_INTERRUPTED"]) {
      expect(dataFailure(code)).not.toBe(m.failure_unknown())
    }
  })

  // `IMPORT_FAILED` says no more than the general sentence, and `USER_GONE` has no one to read it.
  it("Given each code the import writes that says why, Then none falls to the general sentence", () => {
    const codes = [
      "ARCHIVE_UNREADABLE",
      "MANIFEST_MISSING",
      "UNSUPPORTED_FORMAT_VERSION",
      "IMPORT_INTERRUPTED",
    ]
    for (const code of codes) expect(dataFailure(code)).not.toBe(m.failure_unknown())
  })

  it("Given a code `Object.prototype` answers for, Then the user gets the general sentence", () => {
    expect(dataFailure("constructor")).toBe(m.failure_unknown())
    expect(dataFailure(null)).toBe(m.failure_unknown())
  })
})
