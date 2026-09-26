import { describe, expect, it } from "vitest"
import { exportFailure, importFailure } from "../dataFailures"
import { m } from "../paraglide/messages.js"

describe("the sentence a failed export or import shows", () => {
  it("Given each code the export writes, Then none falls to the general sentence", () => {
    for (const code of ["DISK_FULL", "BUILD_FAILED", "EXPORT_INTERRUPTED"]) {
      expect(exportFailure(code)).not.toBe(m.failure_unknown())
    }
  })

  // `USER_GONE` has no one to read it, and a gone cause is never shown as a failure.
  it("Given an export reason with no sentence of its own, Then the user gets the general one", () => {
    for (const code of ["USER_GONE", "EXPIRED", "DELETED", "SUPERSEDED"]) {
      expect(exportFailure(code)).toBe(m.failure_unknown())
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
    for (const code of codes) expect(importFailure(code)).not.toBe(m.failure_unknown())
  })

  it("Given a code `Object.prototype` answers for, Then the user gets the general sentence", () => {
    for (const failure of [exportFailure, importFailure]) {
      expect(failure("constructor")).toBe(m.failure_unknown())
      expect(failure(null)).toBe(m.failure_unknown())
    }
  })
})
