import { describe, expect, it } from "vitest"
import { dragDepth } from "./drags"

describe("dragDepth", () => {
  it("counts a target entered", () => {
    expect(dragDepth(0, "enter")).toBe(1)
    expect(dragDepth(1, "enter")).toBe(2)
  })

  it("keeps the target entered while a child of it is crossed", () => {
    // What a crossing looks like from the container: the child's `dragenter` and the container's
    // own `dragleave`, in that order, neither of which is an exit.
    expect(dragDepth(dragDepth(dragDepth(0, "enter"), "enter"), "leave")).toBe(1)
  })

  it("leaves the target on the last exit", () => {
    expect(dragDepth(1, "leave")).toBe(0)
  })

  it("never falls below nothing, an exit reaching it first being a drag it never saw", () => {
    expect(dragDepth(0, "leave")).toBe(0)
  })

  it("forgets everything on a drop, which fires no exit of its own", () => {
    expect(dragDepth(3, "drop")).toBe(0)
  })
})
