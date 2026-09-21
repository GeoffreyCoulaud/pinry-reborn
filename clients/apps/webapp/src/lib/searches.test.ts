import { describe, expect, it } from "vitest"
import { searchTermOr } from "./searches"

describe("the grid's search term", () => {
  it("Given a term in the address, Then it is the one the grid searches on", () => {
    expect(searchTermOr("a cat")).toBe("a cat")
  })

  it("Given nothing in the address, Then there is no term", () => {
    expect(searchTermOr(undefined)).toBeUndefined()
  })

  it("Given a term the address padded, Then the padding is not part of it", () => {
    expect(searchTermOr("  cat  ")).toBe("cat")
  })

  it("Given a blank term, Then there is no term, the API refusing one", () => {
    expect(searchTermOr("   ")).toBeUndefined()
  })

  it("Given something that is not text, Then there is no term", () => {
    expect(searchTermOr(["cat"])).toBeUndefined()
  })
})
