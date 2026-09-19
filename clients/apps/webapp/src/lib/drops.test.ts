import { describe, expect, it } from "vitest"
import { partitionDrop, type KeptFile } from "./drops"

const kept = (name: string): KeptFile => ({
  file: new File(["ok"], name, { type: "image/png" }),
  measurement: { size: 2, width: 100, height: 100 },
})

describe("partitionDrop", () => {
  it("keeps the files that survived their judgement, in the order the drop carried them", () => {
    const [one, two] = [kept("one.png"), kept("two.png")]
    expect(partitionDrop([one, "TOO_MANY_BYTES", two], [])).toEqual({
      files: [one, two],
      urls: [],
      refusals: ["TOO_MANY_BYTES"],
    })
  })

  it("says each reason once, ten files too heavy being one thing to say", () => {
    expect(partitionDrop(["TOO_MANY_BYTES", "UNREADABLE", "TOO_MANY_BYTES"], []).refusals).toEqual([
      "TOO_MANY_BYTES",
      "UNREADABLE",
    ])
  })

  it("carries the addresses through beside the files, neither taking from the other", () => {
    const one = kept("one.png")
    expect(partitionDrop([one], ["https://example.test/cat.png"])).toEqual({
      files: [one],
      urls: ["https://example.test/cat.png"],
      refusals: [],
    })
  })

  it("speaks for a drop nothing else spoke for, which is the blob: a browser tab hands over", () => {
    expect(partitionDrop([], [])).toEqual({ files: [], urls: [], refusals: ["UNSUPPORTED_DROP"] })
  })

  it("stays silent about a drop an address alone survived", () => {
    expect(partitionDrop([], ["https://example.test/cat.png"]).refusals).toEqual([])
  })
})
