import { describe, expect, it } from "vitest"
import { readPreference, resolveTheme } from "./theme"

describe("readPreference", () => {
  it("keeps a preference it knows", () => {
    expect(readPreference("dark")).toBe("dark")
    expect(readPreference("light")).toBe("light")
    expect(readPreference("system")).toBe("system")
  })

  it("falls back to the system for an absent or unknown value", () => {
    expect(readPreference(null)).toBe("system")
    expect(readPreference("sepia")).toBe("system")
  })
})

describe("resolveTheme", () => {
  it("follows the machine only for the system preference", () => {
    expect(resolveTheme("system", true)).toBe("dark")
    expect(resolveTheme("system", false)).toBe("light")
  })

  it("overrides the machine for a chosen theme", () => {
    expect(resolveTheme("light", true)).toBe("light")
    expect(resolveTheme("dark", false)).toBe("dark")
  })
})
