import { describe, expect, it } from "vitest"
import lockfile from "../../../../pnpm-lock.yaml?raw"
import manifest from "../../package.json"

describe("the lockfile", () => {
  it("Given react-aria a direct dependency, Then the tree holds the one copy react-aria-components uses", () => {
    const resolved = lockfile.match(/^ {2}react-aria@[^:(]+/gm)

    // Its package key and its one snapshot: a drifted pin or a peerless copy adds a key (ADR 0045).
    const key = `  react-aria@${manifest.dependencies["react-aria"]}`
    expect(resolved).toEqual([key, key])
  })
})
