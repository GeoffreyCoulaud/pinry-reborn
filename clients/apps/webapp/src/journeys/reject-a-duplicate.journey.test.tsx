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
	it("Given two pins marked as duplicates, When one is not a duplicate, Then it folds under the rejected, the markers go, and restoring brings both back", async () => {
		const harbour = readyPin("a harbour at dusk");
		const copy = readyPin("the same harbour, smaller", 400, 300);
		const pair: Pair = { pins: [harbour, copy], rejected: false };
		const flagged = (pin: Pin) => ({
			...pin,
			hasPendingDuplicates: !pair.rejected,
		});
		server.use(
			sessionRoute(() => true),
			onePinPage(() => [harbour, copy].map(flagged)),
			http.get("/api/v1/pins/:pinId", ({ params }) =>
				HttpResponse.json(flagged(params.pinId === copy.id ? copy : harbour)),
			),
			...duplicateRoutes([pair]),
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
		const pending = await within(dialog).findByRole("list", {
			name: m.duplicates(),
		});
		const row = within(pending).getByRole("listitem");
		expect(within(row).getByText(copy.description)).toBeVisible();
		expect(
			within(row).getByText(m.media_dimensions({ width: 400, height: 300 })),
		).toBeVisible();
		await user.click(
			within(row).getByRole("button", { name: m.duplicate_reject() }),
		);

		const folded = await within(dialog).findByRole("button", {
			name: m.duplicates_rejected({ count: 1 }),
		});
		expect(
			within(dialog).queryByRole("list", { name: m.duplicates() }),
		).toBeNull();
		await waitFor(() => expect(markers()).toHaveLength(0));

		await user.click(folded);
		await user.click(
			await within(dialog).findByRole("button", {
				name: m.duplicate_restore(),
			}),
		);
		expect(
			await within(dialog).findByRole("list", { name: m.duplicates() }),
		).toBeVisible();
		await waitFor(() => expect(markers()).toHaveLength(2));
	});
});
