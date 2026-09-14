import type { Schemas } from "@pinry-reborn/auth"

/** The two renditions a tile ever asks for, of the four the API serves. */
export type Rendition = "SMALL" | "MEDIUM"

/**
 * The widest column, in device pixels, the small rendition covers without stretching. It is
 * `images.renditions.small`'s default, and it stands in only until the handshake answers with
 * the deployment's own: a deployment that narrowed `small` would upscale every tile visibly.
 */
const SMALL_RENDITION_PX = 240

/**
 * The CSS `aspect-ratio` the tile is placed with. `WaterfallLayout` takes no per-item size and
 * measures the node instead, so a tile that knows its ratio settles into its column at mount,
 * before a byte of the image arrives (specification 4.7, question AA). A tile the API gave no
 * dimensions for is square, which is one measurement like any other.
 */
export function tileAspectRatio(
  width: number | null | undefined,
  height: number | null | undefined,
): string {
  return width != null && height != null ? `${width} / ${height}` : "1 / 1"
}

/** The rendition a column this wide needs, on a display of this pixel ratio. */
export function renditionForColumn(
  columnWidth: number,
  pixelRatio: number,
  smallRenditionPx: number = SMALL_RENDITION_PX,
): Rendition {
  return columnWidth * pixelRatio > smallRenditionPx ? "MEDIUM" : "SMALL"
}

/** The bytes an `<img>` fetches: the relative URL the API gave, at one rendition. */
export function tileImageSource(url: string, rendition: Rendition): string {
  return `${url}?size=${rendition}`
}

/**
 * The pins a page places. A download the server is still running has no dimensions, so its tile
 * would reflow the column when it finished: it stays out until the pin carries its image
 * (specification 4.2).
 */
export function placeableTiles<
  T extends { image?: { status: Schemas["PinImageStateDto"]["status"] } | null },
>(pins: readonly T[]): T[] {
  return pins.filter((pin) => pin.image?.status !== "PENDING")
}

/** A page's pins with the freshly read ones swapped in, the rest left as they were. */
export function replacePins<T extends { id: string }>(pins: readonly T[], fresh: readonly T[]): T[] {
  const byId = new Map(fresh.map((pin) => [pin.id, pin]))
  return pins.map((pin) => byId.get(pin.id) ?? pin)
}
