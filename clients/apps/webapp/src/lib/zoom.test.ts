import { describe, expect, it } from "vitest";
import { UNZOOMED, type View, zoomAt } from "./zoom";

/** The point of the media drawn at this point of the stage, both from its centre. */
const drawnAt = (view: View, at: { x: number; y: number }) => ({
	x: (at.x - view.x) / view.zoom,
	y: (at.y - view.y) / view.zoom,
});

describe("the stage's zoom", () => {
	it("Given a zoom under the pointer, Then the detail under it stays under it", () => {
		const before = { zoom: 2, x: 30, y: -12 };
		const pointer = { x: 100, y: -40 };

		const after = zoomAt(before, 1.5, pointer);

		expect(after.zoom).toBe(3);
		expect(drawnAt(after, pointer)).toEqual(drawnAt(before, pointer));
	});

	it("Given a zoom past 800 %, Then it stops at 800 %", () => {
		expect(zoomAt({ zoom: 6, x: 0, y: 0 }, 2).zoom).toBe(8);
	});

	it("Given a zoom out past 100 %, Then the view is whole and centred again", () => {
		expect(zoomAt({ zoom: 2, x: 30, y: -12 }, 0.25)).toEqual(UNZOOMED);
	});
});
