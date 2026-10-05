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
	type Pair,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** A tile or a dialog's image by its description, the grid behind an open dialog included. */
const image = (description: string) =>
	screen.queryByRole("img", { name: description, hidden: true });

describe("merge a group of duplicates", () => {
	it("Given a pin with three duplicates, When the third is kept and the fourth left out, Then the dialog shows the kept pin, the absorbed ones leave the grid, and the boards are read again", async () => {
		const harbours = board("Harbours", "", 4);
		const [open, smaller, kept, noon] = [
			readyPin("a harbour at dusk"),
			readyPin("the same harbour, smaller"),
			readyPin("the same harbour, cropped"),
			readyPin("the same harbour at noon"),
		].map((one) => ({ ...one, hasPendingDuplicates: true }));
		const pairs: Pair[] = [smaller, kept, noon].map((other) => ({
			pins: [open, other],
			rejected: false,
		}));
		const merged = { ...kept, tags: [{ name: "boats" }] };
		let merges: unknown[] = [];
		let boardReads = 0;
		server.use(
			sessionRoute(() => true),
			http.get("/api/v1/boards", () => {
				boardReads++;
				return HttpResponse.json({ boards: [harbours] });
			}),
			boardPinsRoute({ [harbours.id]: [[open, smaller, kept, noon]] }),
			...duplicateRoutes(pairs),
			http.post("/api/v1/pins/merges", async ({ request }) => {
				merges = [...merges, await request.json()];
				// The absorbed pins are recycled, which hides their pairs (decision J).
				pairs.splice(0, pairs.length);
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
	});
});
