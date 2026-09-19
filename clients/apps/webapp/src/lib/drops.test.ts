import { describe, expect, it } from "vitest"
import {
  entriesOf,
  partitionDrop,
  withDrop,
  type DropPartition,
  type KeptFile,
  type PinEntry,
} from "./drops"

const kept = (name: string): KeptFile => ({
  file: new File(["ok"], name, { type: "image/png" }),
  measurement: { size: 2, width: 100, height: 100 },
})

/** A drop already judged, which is what both functions under test are handed. */
const drop = (files: KeptFile[] = [], urls: string[] = []): DropPartition => ({
  files,
  urls,
  refusals: [],
})

const entry = (file: KeptFile | null = null, url = ""): PinEntry => ({ file, url })

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

describe("entriesOf", () => {
  it("makes one entry of a file and the address beside it, which are one pin and not two", () => {
    const one = kept("one.png")
    expect(entriesOf(drop([one], ["https://example.test/cat.png"]))).toEqual([
      entry(one, "https://example.test/cat.png"),
    ])
  })

  it("makes one entry per element of the longer side", () => {
    const [one, two] = [kept("one.png"), kept("two.png")]
    expect(entriesOf(drop([one, two], ["https://example.test/cat.png"]))).toEqual([
      entry(one, "https://example.test/cat.png"),
      entry(two),
    ])
    expect(entriesOf(drop([], ["https://example.test/a.png", "https://example.test/b.png"]))).toEqual(
      [entry(null, "https://example.test/a.png"), entry(null, "https://example.test/b.png")],
    )
  })

  it("holds one blank entry for a form opened by hand, which no drop filled", () => {
    expect(entriesOf(null)).toEqual([entry()])
  })
})

describe("withDrop", () => {
  it("corrects the entry being worked on when one element arrives, and leaves the rest alone", () => {
    const [first, second, arriving] = [kept("one.png"), kept("two.png"), kept("better.png")]
    expect(withDrop([entry(first), entry(second)], 1, drop([arriving]))).toEqual([
      entry(first),
      entry(arriving),
    ])
  })

  it("takes nothing away: an address arriving alone keeps the file already chosen", () => {
    const one = kept("one.png")
    expect(withDrop([entry(one, "https://example.test/old.png")], 0, drop([], []))).toEqual([
      entry(one, "https://example.test/old.png"),
    ])
    expect(
      withDrop([entry(one, "https://example.test/old.png")], 0, drop([], ["https://example.test/new.png"])),
    ).toEqual([entry(one, "https://example.test/new.png")])
  })

  it("sends several arriving to the end of the queue, which is add these and not replace mine", () => {
    const [first, second, third] = [kept("one.png"), kept("two.png"), kept("three.png")]
    expect(withDrop([entry(first)], 0, drop([second, third]))).toEqual([
      entry(first),
      entry(second),
      entry(third),
    ])
  })
})
