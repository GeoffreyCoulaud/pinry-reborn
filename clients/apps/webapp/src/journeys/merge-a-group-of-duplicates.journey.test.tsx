import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import {
	board,
	boardPinsRoute,
	downloadsRoute,
	duplicateRoutes,
	handshakeRoute,
	onePinPage,
	type Pair,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** A tile or a dialog's image by its description, the grid behind an open dialog included. */
const image = (description: string) =>
	screen.queryByRole("img", { name: description, hidden: true });

const marked = (description: string) => ({
	...readyPin(description),
	hasPendingDuplicates: true,
});

describe("merge a group of duplicates", () => {
	it("Given a pin with three duplicates, When the third is kept and the fourth left out, Then the dialog shows the kept pin, the absorbed ones leave the grid, and the boards are read again", async () => {
		const harbours = board("Harbours", "", 4);
		const open = marked("a harbour at dusk");
		const smaller = marked("the same harbour, smaller");
		const kept = marked("the same harbour, cropped");
		const noon = marked("the same harbour at noon");
		const pairs: Pair[] = [smaller, kept, noon].map((other) => ({
			pins: [open, other],
			rejected: false,
		}));
		const merged = {
			...kept,
			tags: [{ name: "boats" }],
			hasPendingDuplicates: false,
		};
		const catalogue = [open, smaller, kept, noon];
		let merges: unknown[] = [];
		let boardReads = 0;
		server.use(
			sessionRoute(() => true),
			http.get("/api/v1/boards", () => {
				boardReads++;
				return HttpResponse.json({ boards: [harbours] });
			}),
			boardPinsRoute({ [harbours.id]: [catalogue] }),
			...duplicateRoutes(pairs),
			http.post("/api/v1/pins/merges", async ({ request }) => {
				merges = [...merges, await request.json()];
				// The absorbed pins are recycled, which hides their pairs and so the noon pin's marker
				// (specification 2026-10-05, decision J).
				pairs.splice(0, pairs.length);
				catalogue.splice(0, catalogue.length, merged, {
					...noon,
					hasPendingDuplicates: false,
				});
				return HttpResponse.json(merged);
			}),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp(`/boards/${harbours.id}`);
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: open.description }),
		);
		const dialog = await screen.findByRole("dialog");
		expect(
			await within(dialog).findByRole("radio", { name: m.merge_keep_this() }),
		).toBeChecked();
		expect(
			within(dialog).getByRole("button", { name: m.merge({ count: 4 }) }),
		).toBeEnabled();
		await user.click(
			within(dialog).getByRole("radio", {
				name: m.merge_keep_pin({ description: kept.description }),
			}),
		);
		await user.click(
			within(dialog).getByRole("checkbox", {
				name: m.merge_include_pin({ description: noon.description }),
			}),
		);
		const readsBefore = boardReads;
		await user.click(
			within(dialog).getByRole("button", { name: m.merge({ count: 3 }) }),
		);

		expect(
			await screen.findByRole("dialog", { name: kept.description }),
		).toBeVisible();
		expect(merges).toEqual([
			{ keptPinId: kept.id, absorbedPinIds: [open.id, smaller.id] },
		]);
		expect(within(screen.getByRole("dialog")).getByText("boats")).toBeVisible();
		expect(image(open.description)).toBeNull();
		expect(image(smaller.description)).toBeNull();
		expect(image(noon.description)).not.toBeNull();
		await waitFor(() => expect(boardReads).toBeGreaterThan(readsBefore));
		await waitFor(() =>
			expect(
				screen.queryAllByRole("img", {
					name: m.duplicates_badge(),
					hidden: true,
				}),
			).toHaveLength(0),
		);
	});

	it("Given a duplicate the grid has not loaded, When it is kept, Then the dialog shows it as the API reads it now, and it steps nowhere", async () => {
		const open = marked("a harbour at dusk");
		const far = marked("the same harbour, far down the catalogue");
		const edited = { ...far, description: "the same harbour, edited since" };
		server.use(
			sessionRoute(() => true),
			onePinPage(() => [open, readyPin("a cat asleep")]),
			...duplicateRoutes([{ pins: [open, far], rejected: false }]),
			http.post("/api/v1/pins/merges", () => HttpResponse.json(far)),
			http.get(`/api/v1/pins/${far.id}`, () => HttpResponse.json(edited)),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: open.description }),
		);
		const dialog = await screen.findByRole("dialog");
		await user.click(
			await within(dialog).findByRole("radio", {
				name: m.merge_keep_pin({ description: far.description }),
			}),
		);
		await user.click(
			within(dialog).getByRole("button", { name: m.merge({ count: 2 }) }),
		);

		const shown = await screen.findByRole("dialog", {
			name: edited.description,
		});
		for (const name of [m.pin_previous(), m.pin_next()]) {
			expect(within(shown).getByRole("button", { name })).toBeDisabled();
		}
	});
});
