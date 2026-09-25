import { describe, expect, it } from "vitest"
import { exportRefusal, importRefusal } from "../dataRefusals"
import { m } from "../paraglide/messages.js"

describe("the sentence a refused export request shows", () => {
  it("Given the client's own encoding defect, Then the user gets the general sentence", () => {
    expect(exportRefusal("UNSUPPORTED_REAUTHENTICATION_FACTOR")).toBe(m.account_refused())
  })

  it("Given a code `Object.prototype` answers for, Then the user gets the general sentence", () => {
    // `constructor` reaches an object, which React throws on as a child, taking the screen down.
    expect(exportRefusal("constructor")).toBe(m.account_refused())
    expect(exportRefusal(null)).toBe(m.account_refused())
  })
})

describe("the sentence a stopped import shows", () => {
  it("Given a code `Object.prototype` answers for, Then the user gets the general sentence", () => {
    expect(importRefusal("constructor")).toBe(m.account_refused())
    expect(importRefusal("CHUNK_TOO_LARGE")).toBe(m.import_chunk_too_large())
  })
})
