import { describe, expect, it } from "vitest"
import {
  placeableTiles,
  renditionForColumn,
  replacePins,
  tileAspectRatio,
  tileImageSource,
} from "./tiles"

describe("a tile's ratio", () => {
  it("Given the dimensions the API measured, Then the tile is placed at that ratio", () => {
    expect(tileAspectRatio(800, 600)).toBe("800 / 600")
  })

  it("Given an image the API never measured, Then the tile is placed square", () => {
    expect(tileAspectRatio(null, 600)).toBe("1 / 1")
    expect(tileAspectRatio(800, null)).toBe("1 / 1")
    expect(tileAspectRatio(undefined, undefined)).toBe("1 / 1")
  })
})

describe("a tile's rendition", () => {
  it("Given a column no wider than the small rendition, Then the small one is enough", () => {
    expect(renditionForColumn(240, 1)).toBe("SMALL")
  })

  it("Given a column the small rendition would stretch, Then the medium one is asked for", () => {
    expect(renditionForColumn(320, 1)).toBe("MEDIUM")
  })

  it("Given a dense display, Then the column's device pixels are what the choice reads", () => {
    expect(renditionForColumn(200, 2)).toBe("MEDIUM")
  })

  it("Given a column no layout has measured yet, Then the narrowest rendition is asked for", () => {
    expect(renditionForColumn(0, 1)).toBe("SMALL")
  })

  it("Given a deployment that narrowed its small rendition, Then the medium one is asked for sooner", () => {
    expect(renditionForColumn(200, 1, 120)).toBe("MEDIUM")
    expect(renditionForColumn(100, 1, 120)).toBe("SMALL")
  })
})

describe("a tile's source", () => {
  it("Given the relative URL the API gave, Then the rendition is a parameter on it", () => {
    expect(tileImageSource("/api/v1/pins/7/image", "MEDIUM")).toBe("/api/v1/pins/7/image?size=MEDIUM")
  })
})

describe("the tiles a page places", () => {
  const withStatus = (id: string, status: "NONE" | "PENDING" | "READY" | "FAILED") => ({
    id,
    image: { status },
  })

  it("Given a download the server is still running, Then the pin has no tile yet", () => {
    const pins = [withStatus("ready", "READY"), withStatus("pending", "PENDING")]

    expect(placeableTiles(pins).map((pin) => pin.id)).toEqual(["ready"])
  })

  it("Given a pin the API reports no image for at all, Then it is placed like any other", () => {
    const pins = [
      { id: "bare", image: null },
      withStatus("failed", "FAILED"),
      withStatus("none", "NONE"),
    ]

    expect(placeableTiles(pins).map((pin) => pin.id)).toEqual(["bare", "failed", "none"])
  })
})

describe("a page's pins after a download settled", () => {
  it("Given the pin read again, Then the page carries it and its neighbours are untouched", () => {
    const page = [
      { id: "a", n: 1 },
      { id: "b", n: 1 },
    ]

    expect(replacePins(page, [{ id: "b", n: 2 }])).toEqual([
      { id: "a", n: 1 },
      { id: "b", n: 2 },
    ])
  })

  it("Given a pin no page holds, Then the page is unchanged", () => {
    const page = [{ id: "a", n: 1 }]

    expect(replacePins(page, [{ id: "z", n: 2 }])).toEqual(page)
  })
})
