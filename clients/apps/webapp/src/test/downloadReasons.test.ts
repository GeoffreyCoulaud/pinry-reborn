import { describe, expect, it } from "vitest"
import { downloadReason } from "../downloadReasons"

describe("the sentence a failed download shows", () => {
  it("Given a code `Object.prototype` answers for, Then the server's own message is what is read", () => {
    // `reasonCode` is the server's own string, and the table it keys must answer for the codes
    // this bundle wrote and for nothing else. `passwordRefusals.ts` copied this shape.
    expect(downloadReason("constructor", "the server said so")).toBe("the server said so")
    expect(downloadReason("toString", null)).toBeNull()
  })
})
