import { describe, expect, it } from "vitest"
import { urisFromDrop } from "./uris"

describe("urisFromDrop", () => {
  it("reads the one address a picture dragged from another tab carries", () => {
    expect(urisFromDrop("https://example.test/cat.png")).toEqual(["https://example.test/cat.png"])
  })

  it("keeps the order the drop stated, the lines being separated by CRLF", () => {
    expect(urisFromDrop("https://example.test/one.png\r\nhttp://example.test/two.png\r\n")).toEqual([
      "https://example.test/one.png",
      "http://example.test/two.png",
    ])
  })

  it("drops the comment lines the format allows", () => {
    // `#` opens a comment in `text/uri-list`, and Firefox writes the page's title on one.
    expect(urisFromDrop("# a cat asleep\r\nhttps://example.test/cat.png")).toEqual([
      "https://example.test/cat.png",
    ])
  })

  it("keeps nothing a server could not fetch from its own position", () => {
    // A browser tab hands over a `blob:` for an image it holds in memory, and a file manager a
    // `file:` that names a path on the machine the browser runs on.
    expect(urisFromDrop("blob:https://example.test/0f5c\r\nfile:///home/cat.png\r\ndata:,cat")).toEqual(
      [],
    )
  })

  it("reads the scheme whatever its case, and ignores the blank lines around it", () => {
    expect(urisFromDrop("\r\n  HTTPS://example.test/cat.png  \r\n\r\n")).toEqual([
      "HTTPS://example.test/cat.png",
    ])
  })

  it("carries nothing out of a drop that stated no address at all", () => {
    expect(urisFromDrop("")).toEqual([])
  })
})
