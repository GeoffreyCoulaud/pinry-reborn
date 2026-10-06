import type { Schemas } from "@pinry-reborn/auth";

/** The two renditions a tile ever asks for, of the four the API serves. */
export type Rendition = "SMALL" | "MEDIUM";

/**
 * The widest column, in device pixels, the small rendition covers without stretching. It is
 * `media.renditions.small`'s default, and it stands in only until the handshake answers with
 * the deployment's own: a deployment that narrowed `small` would upscale every tile visibly.
 */
const SMALL_RENDITION_PX = 240;

/**
 * The CSS `aspect-ratio` the tile is placed with. `WaterfallLayout` takes no per-item size and
 * measures the node instead, so a tile that knows its ratio settles into its column at mount,
 * before a byte of the image arrives (specification 2026-09-10, 4.7 and question AA). A tile the
 * API gave no dimensions for is square, which is one measurement like any other.
 */
export function tileAspectRatio(
	width: number | null | undefined,
	height: number | null | undefined,
): string {
	return width != null && height != null ? `${width} / ${height}` : "1 / 1";
}

/** The rendition a column this wide needs, on a display of this pixel ratio. */
export function renditionForColumn(
	columnWidth: number,
	pixelRatio: number,
	smallRenditionPx: number = SMALL_RENDITION_PX,
): Rendition {
	return columnWidth * pixelRatio > smallRenditionPx ? "MEDIUM" : "SMALL";
}

/** The bytes an `<img>` fetches: the relative URL the API gave, at one rendition. */
export function tileMediaSource(url: string, rendition: Rendition): string {
	return `${url}?size=${rendition}`;
}

/** A video's first seconds as an animated image, at the tile's rendition (decision D1). */
export function tileAnimatedSource(url: string, rendition: Rendition): string {
	return `${tileMediaSource(url, rendition)}&animated=true`;
}

/** An image's still frame or a video's poster, never moving (specification 2026-10-05, decision F). */
export function tileStillSource(url: string, rendition: Rendition): string {
	return `${tileMediaSource(url, rendition)}&animated=false`;
}

/** The one test of what the grid places, which the viewer's order shares (decision F). */
function isPlaceable(pin: {
	media?: { status: Schemas["PinMediaStateDto"]["status"] } | null;
}): boolean {
	return pin.media?.status !== "PENDING";
}

/**
 * The pins a page places. A download the server is still running has no dimensions, so its tile
 * would reflow the column when it finished: it stays out until the pin carries its image
 * (specification 4.2).
 */
export function placeableTiles<
	T extends {
		media?: { status: Schemas["PinMediaStateDto"]["status"] } | null;
	},
>(pins: readonly T[]): T[] {
	return pins.filter(isPlaceable);
}

/**
 * The pins the viewer steps to from the opened one, in the grid's order: the placeable tiles, the
 * opened pin kept in though its download is running (specification 2026-09-27, decision F).
 */
export function neighbours<
	T extends {
		id: string;
		media?: { status: Schemas["PinMediaStateDto"]["status"] } | null;
	},
>(
	pins: readonly T[],
	openedId: string | null,
): { previous: T | undefined; next: T | undefined } {
	const order = pins.filter((pin) => pin.id === openedId || isPlaceable(pin));
	const at = order.findIndex((pin) => pin.id === openedId);
	return at < 0
		? { previous: undefined, next: undefined }
		: { previous: order[at - 1], next: order[at + 1] };
}

/** A page's pins with the freshly read ones swapped in, the rest left as they were. */
export function replacePins<T extends { id: string }>(
	pins: readonly T[],
	fresh: readonly T[],
): T[] {
	const byId = new Map(fresh.map((pin) => [pin.id, pin]));
	return pins.map((pin) => byId.get(pin.id) ?? pin);
}

/**
 * The cached pages with the deleted pins taken out of them. A page left with no pin is kept: the
 * cursors run page to page, and dropping one breaks the chain the next fetch reads (specification
 * 2026-09-20, decision P).
 */
export function removePins<Page extends { pins: { id: string }[] }>(
	pages: readonly Page[],
	deleted: readonly string[],
): Page[] {
	const gone = new Set(deleted);
	return pages.map((page) => ({
		...page,
		pins: page.pins.filter((pin) => !gone.has(pin.id)),
	}));
}
