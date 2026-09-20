import { describe, expect, it } from "vitest"
import { pinSortOr, recycledPinSortOr } from "./sorts"

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

describe("the recycle bin's order", () => {
  it("Given nothing in the address, Then the pins just deleted come first", () => {
    expect(recycledPinSortOr(undefined)).toBe("DELETED_AT_DESC")
  })

  it("Given the order the bin alone serves, Then it is the one the bin asks for", () => {
    expect(recycledPinSortOr("DELETED_AT_DESC")).toBe("DELETED_AT_DESC")
  })

  it("Given an order the grid serves too, Then the bin takes it", () => {
    expect(recycledPinSortOr("CREATED_AT_ASC")).toBe("CREATED_AT_ASC")
  })

  it("Given an order the API would refuse, Then the default stands in for it", () => {
    expect(recycledPinSortOr("DELETED_AT_ASC")).toBe("DELETED_AT_DESC")
  })
})
