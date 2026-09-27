import { describe, expect, it } from "vitest"
import { downloadReason, retriable } from "../downloadReasons"

describe("the sentence a failed download shows", () => {
  it("Given a code `Object.prototype` answers for, Then the server's own message is what is read", () => {
    // `reasonCode` is the server's own string, and the table it keys must answer for the codes
    // this bundle wrote and for nothing else. `passwordRefusals.ts` copied this shape.
    expect(downloadReason("constructor", "the server said so")).toBe("the server said so")
    expect(downloadReason("toString", null)).toBeNull()
  })
})

describe("whether a failed download is offered again", () => {
  it("Given a code this bundle does not know, Then it is not retriable", () => {
    expect(retriable("UNREACHABLE")).toBe(true)
    expect(retriable("FETCH_FAILED")).toBe(false)
    expect(retriable("A_REASON_ADDED_AFTER_THIS_BUNDLE")).toBe(false)
    expect(retriable("constructor")).toBe(false)
    expect(retriable(null)).toBe(false)
  })
})
