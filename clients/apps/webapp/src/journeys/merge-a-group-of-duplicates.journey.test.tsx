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

const marked = (description: string, width = 800, height = 600) => ({
	...readyPin(description, width, height),
	hasPendingDuplicates: true,
});

const pending = (pins: Pair["pins"]): Pair => ({ pins, rejected: false });

/** The comparator, opened from the dialog's row of duplicates. */
async function compare(user: ReturnType<typeof userEvent.setup>) {
	const dialog = await screen.findByRole("dialog");
	await user.click(
		await within(dialog).findByRole("button", { name: m.compare() }),
	);
	await within(dialog).findByRole("heading", { name: m.compare_heading() });
	return dialog;
}

describe("merge a group of duplicates", () => {
	it("Given a pin with three duplicates, When it is merged into the larger one and the fourth rejected, Then the dialog shows the kept pin, the merged ones leave the grid, and the boards are read again", async () => {
		const harbours = board("Harbours", "", 4);
		const open = marked("a harbour at dusk");
		const smaller = marked("the same harbour, smaller", 400, 300);
		const larger = marked("the same harbour, larger", 1600, 1200);
		const noon = marked("the same harbour at noon");
		const pairs = [smaller, larger, noon].map((other) =>
			pending([open, other]),
		);
		const merged = {
			...larger,
			tags: [{ name: "boats" }],
			hasPendingDuplicates: false,
		};
		const catalogue = [open, smaller, larger, noon];
		let resolutions: unknown[] = [];
		let boardReads = 0;
		server.use(
			sessionRoute(() => true),
			http.get("/api/v1/boards", () => {
				boardReads++;
				return HttpResponse.json({ boards: [harbours] });
			}),
			boardPinsRoute({ [harbours.id]: [catalogue] }),
			http.post(
				`/api/v1/pins/${open.id}/duplicates/resolutions`,
				async ({ request }) => {
					resolutions = [...resolutions, await request.json()];
					// The merged pins are recycled, which hides their pairs; noon's is rejected.
					pairs.splice(0, pairs.length);
					catalogue.splice(0, catalogue.length, merged, {
						...noon,
						hasPendingDuplicates: false,
					});
					return HttpResponse.json(merged);
				},
			),
			...duplicateRoutes(pairs),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp(`/boards/${harbours.id}`);
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: open.description }),
		);
		const dialog = await compare(user);
		expect(
			within(dialog).getByRole("button", {
				name: m.compare_merge_count({ count: 4 }),
			}),
		).toBeEnabled();
		const versions = within(dialog).getByRole("list", {
			name: m.compare_versions(),
		});
		await user.click(
			within(versions).getByRole("button", { name: noon.description }),
		);
		await user.click(
			within(dialog).getByRole("radio", { name: m.duplicate_reject() }),
		);
		const readsBefore = boardReads;
		await user.click(
			within(dialog).getByRole("button", {
				name: m.compare_merge_count({ count: 3 }),
			}),
		);

		expect(
			await screen.findByRole("dialog", { name: larger.description }),
		).toBeVisible();
		expect(resolutions).toEqual([
			{
				decisions: {
					[open.id]: "MERGE",
					[smaller.id]: "MERGE",
					[larger.id]: "KEEP",
					[noon.id]: "REJECT",
				},
			},
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

	it("Given a larger duplicate the grid has not loaded, When the group is merged, Then the dialog shows it as the API reads it now, and it steps nowhere", async () => {
		const open = marked("a harbour at dusk");
		const far = marked("the same harbour, far down the catalogue", 1600, 1200);
		const edited = { ...far, description: "the same harbour, edited since" };
		server.use(
			sessionRoute(() => true),
			onePinPage(() => [open, readyPin("a cat asleep")]),
			...duplicateRoutes([pending([open, far])]),
			http.get(`/api/v1/pins/${far.id}`, () => HttpResponse.json(edited)),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: open.description }),
		);
		const dialog = await compare(user);
		await user.click(
			within(dialog).getByRole("button", {
				name: m.compare_merge_count({ count: 2 }),
			}),
		);

		const shown = await screen.findByRole("dialog", {
			name: edited.description,
		});
		for (const name of [m.pin_previous(), m.pin_next()]) {
			expect(within(shown).getByRole("button", { name })).toBeDisabled();
		}
	});
});
