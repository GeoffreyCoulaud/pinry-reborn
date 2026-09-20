import { describe, expect, it } from "vitest"
import { pinSortOr } from "./sorts"

describe("the grid's order", () => {
  it("Given an order the API serves, Then it is the one the grid asks for", () => {
    expect(pinSortOr("CREATED_AT_ASC")).toBe("CREATED_AT_ASC")
  })

  it("Given nothing in the address, Then the newest pins come first", () => {
    expect(pinSortOr(undefined)).toBe("CREATED_AT_DESC")
  })

  it("Given an order the API would refuse, Then the default stands in for it", () => {
    expect(pinSortOr("OLDEST_ON_A_TUESDAY")).toBe("CREATED_AT_DESC")
  })
})
