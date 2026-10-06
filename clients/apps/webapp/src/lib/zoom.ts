/** Both sides' shared zoom, and their pan in pixels from the stage's centre. */
export interface View {
	zoom: number;
	x: number;
	y: number;
}

export const UNZOOMED: View = { zoom: 1, x: 0, y: 0 };

/** 800 %, past which a pixel fills more of the stage than any comparison needs (specification 2026-10-05-the-duplicates-are-compared, decision A). */
export const MAX_ZOOM = 8;

/** Zoomed by `factor`, the detail under `point` (from the stage's centre) kept there; centred again at 100 %. */
export function zoomAt(
	view: View,
	factor: number,
	point: { x: number; y: number } = { x: 0, y: 0 },
): View {
	const zoom = Math.min(MAX_ZOOM, Math.max(1, view.zoom * factor));
	if (zoom === 1) {
		return UNZOOMED;
	}
	const ratio = zoom / view.zoom;
	return {
		zoom,
		x: point.x - ratio * (point.x - view.x),
		y: point.y - ratio * (point.y - view.y),
	};
}
