import { describe, expect, it } from "vitest"

/** Vite resolves the patterns at transform time, so what is asserted is the sources on disk. */
const sources = import.meta.glob("../routes/*.tsx", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>
const router = import.meta.glob("../router.tsx", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>

const REDIRECT = 'Navigate to="/sign-in"'

describe("the session guard", () => {
  it("Given the route components, Then none of them sends a visitor away itself", () => {
    const guarding = Object.keys(sources).filter((path) => sources[path]?.includes(REDIRECT))

    expect(guarding).toEqual([])
  })

  it("Given the router, Then the redirect is written there once", () => {
    const written = Object.values(router).join("").split(REDIRECT).length - 1

    expect(written).toBe(1)
  })

  it("Given the router, Then every route but the two credentials ones is wrapped", () => {
    const source = Object.values(router).join("")
    const occurrences = (needle: string) => source.split(needle).length - 1

    // The redirect lives inside `guarded`, so dropping the wrapper from a declaration leaves both
    // assertions above green and the screen open to a visitor. This is what counts the call sites.
    expect(occurrences("component: guarded(")).toBe(occurrences("createRoute(") - 2)
  })
})
