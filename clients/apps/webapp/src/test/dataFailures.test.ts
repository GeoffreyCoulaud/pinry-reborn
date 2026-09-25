import { describe, expect, it } from "vitest"
import { dataFailure } from "../dataFailures"
import { m } from "../paraglide/messages.js"

describe("the sentence a failed export shows", () => {
  it("Given each code the export writes, Then none falls to the general sentence", () => {
    for (const code of ["DISK_FULL", "BUILD_FAILED", "EXPORT_INTERRUPTED"]) {
      expect(dataFailure(code)).not.toBe(m.failure_unknown())
    }
  })

  it("Given a code `Object.prototype` answers for, Then the user gets the general sentence", () => {
    expect(dataFailure("constructor")).toBe(m.failure_unknown())
    expect(dataFailure(null)).toBe(m.failure_unknown())
  })
})
