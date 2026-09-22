import { describe, expect, it } from "vitest"
import { refusalCode } from "./refusals"

describe("the code a refusal carries", () => {
  it("Given a problem body, Then its code is the answer", () => {
    const body = { status: 403, code: "REAUTHENTICATION_FAILED" }

    expect(refusalCode(body)).toBe("REAUTHENTICATION_FAILED")
  })

  it("Given a body that is not JSON, Then there is no code", () => {
    expect(refusalCode("Not Allowed")).toBeNull()
  })

  it("Given no body at all, Then there is no code", () => {
    expect(refusalCode(null)).toBeNull()
  })

  it("Given a body carrying no code, Then there is no code", () => {
    expect(refusalCode({ status: 403 })).toBeNull()
  })

  it("Given a code that is not a string, Then there is no code", () => {
    expect(refusalCode({ code: 403 })).toBeNull()
  })
})
