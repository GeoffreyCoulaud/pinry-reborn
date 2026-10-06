import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
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

/** The tiles' markers, the grid behind an open dialog included. */
const markers = () =>
	screen.queryAllByRole("img", { name: m.duplicates_badge(), hidden: true });

describe("reject a duplicate", () => {
	it("Given two pins marked as duplicates, When one is not a duplicate, Then the markers go, and Review shows it rejected and merges it", async () => {
		const harbour = readyPin("a harbour at dusk");
		const copy = readyPin("the same harbour, smaller", 400, 300);
		const pairs: Pair[] = [{ pins: [harbour, copy], rejected: false }];
		const flagged = (pin: Pin) => ({
			...pin,
			hasPendingDuplicates: pairs.some((pair) => !pair.rejected),
		});
		server.use(
			sessionRoute(() => true),
			// The copy is recycled once merged, which hides its pair.
			onePinPage(() =>
				(pairs.length > 0 ? [harbour, copy] : [harbour]).map(flagged),
			),
			http.get("/api/v1/pins/:pinId", ({ params }) =>
				HttpResponse.json(flagged(params.pinId === copy.id ? copy : harbour)),
			),
			...duplicateRoutes(pairs),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: harbour.description }),
		);
		expect(markers()).toHaveLength(2);
		const dialog = await screen.findByRole("dialog");
		expect(
			await within(dialog).findByText(m.duplicates_pending({ count: 1 })),
		).toBeVisible();
		await user.click(within(dialog).getByRole("button", { name: m.compare() }));
		await user.click(
			await within(dialog).findByRole("radio", { name: m.duplicate_reject() }),
		);
		await user.click(
			within(dialog).getByRole("button", {
				name: m.compare_reject_count({ count: 1 }),
			}),
		);

		expect(
			await within(dialog).findByText(m.duplicates_rejected({ count: 1 })),
		).toBeVisible();
		await waitFor(() => expect(markers()).toHaveLength(0));

		await user.click(within(dialog).getByRole("button", { name: m.review() }));
		expect(
			await within(dialog).findByRole("button", { name: m.compare_nothing() }),
		).toBeDisabled();
		expect(
			within(dialog).getByRole("radio", { name: m.duplicate_reject() }),
		).toBeChecked();
		await user.click(
			within(dialog).getByRole("radio", { name: m.compare_merge() }),
		);
		await user.click(
			within(dialog).getByRole("button", {
				name: m.compare_merge_count({ count: 2 }),
			}),
		);

		await waitFor(() =>
			expect(
				screen.queryByRole("img", { name: copy.description, hidden: true }),
			).toBeNull(),
		);
		expect(
			within(dialog).queryByRole("button", { name: m.review() }),
		).toBeNull();
	});
});
