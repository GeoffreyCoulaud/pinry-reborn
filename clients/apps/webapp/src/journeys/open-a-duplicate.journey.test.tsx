import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import {
	downloadsRoute,
	duplicateRoutes,
	handshakeRoute,
	pinsRoute,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** Whether the open dialog steps anywhere. */
function steps(dialog: HTMLElement) {
	return [m.pin_previous(), m.pin_next()].map((name) =>
		within(dialog).getByRole("button", { name }).hasAttribute("disabled"),
	);
}

describe("open a duplicate", () => {
	it("Given a duplicate the grid has not loaded, When it is opened from the list, Then the dialog shows it, and it steps nowhere", async () => {
		const harbour = {
			...readyPin("a harbour at dusk"),
			hasPendingDuplicates: true,
		};
		const unloaded = {
			...readyPin("the same harbour, far down the catalogue"),
			hasPendingDuplicates: true,
		};
		server.use(
			sessionRoute(() => true),
			pinsRoute([
				[readyPin("a lighthouse"), harbour, readyPin("a cat asleep")],
			]),
			...duplicateRoutes([{ pins: [harbour, unloaded], rejected: false }]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: harbour.description }),
		);
		const fromGrid = await screen.findByRole("dialog");
		expect(steps(fromGrid)).toEqual([false, false]);
		await user.click(
			await within(fromGrid).findByRole("button", {
				name: /far down the catalogue/,
			}),
		);

		const fromList = await screen.findByRole("dialog", {
			name: unloaded.description,
		});
		expect(within(fromList).getByText(unloaded.description)).toBeVisible();
		expect(steps(fromList)).toEqual([true, true]);

		// Back through the duplicate's own list, to a pin the grid holds: still no stepping.
		await user.click(
			await within(fromList).findByRole("button", {
				name: new RegExp(harbour.description),
			}),
		);
		const back = await screen.findByRole("dialog", {
			name: harbour.description,
		});
		expect(steps(back)).toEqual([true, true]);
	});
});
