import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	boardRoutes,
	downloadsRoute,
	handshakeRoute,
	onePinPage,
	pin,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** Where the picture was found. The server stores it on an upload too, and never fetches it. */
const FOUND_AT = "https://example.test/harbour.png";

const aPicture = () => new File(["ok"], "harbour.png", { type: "image/png" });

/** The pin's image state, rebuilt rather than spread: the field is optional on the pin. */
function mediaOf(
	shown: Pin,
	change: Partial<NonNullable<Pin["media"]>>,
): Pin["media"] {
	return { status: "READY", url: `/api/v1/pins/${shown.id}/media`, ...change };
}

/** What each write carried, in the order the two arrived: the pin's body, then the image's. */
type Written =
	| { pin: { sourceMediaUrl: string | null } }
	| { media: string | { sourceUrl: string } | null };

function recorder() {
	return { written: [] as Written[] };
}

/**
 * The catalogue, served from the pin as the journey holds it now, the write of the pin, and the
 * one route every image option calls. A file answers `201`, an address `202`.
 */
function account(
	current: () => Pin,
	record: ReturnType<typeof recorder>,
	{
		reread = current,
		mediaStatus = 202,
	}: { reread?: () => Pin; mediaStatus?: number } = {},
) {
	server.use(
		sessionRoute(() => true),
		onePinPage(() => [current()]),
		http.put("/api/v1/pins/:pinId", async ({ request }) => {
			record.written.push({
				pin: (await request.json()) as { sourceMediaUrl: string | null },
			});
			return HttpResponse.json(reread());
		}),
		// What the write rereads into the pages the grid holds (specification 2026-09-20, decision P),
		// served as the pin was read: a tile that changes shape can then only have followed the write.
		http.get("/api/v1/pins/:pinId", () => HttpResponse.json(reread())),
		http.put("/api/v1/pins/:pinId/media", async ({ request }) => {
			const type = request.headers.get("content-type")?.split(";")[0] ?? null;
			// A multipart body is read by its media type alone: the parts do not survive a jsdom
			// upload the same way on every Node the gate and a workstation run.
			const body = type === "application/json" ? await request.json() : type;
			record.written.push({
				media: body as { sourceUrl: string } | string | null,
			});
			return mediaStatus < 300
				? HttpResponse.json({ status: "PENDING" }, { status: mediaStatus })
				: new HttpResponse(null, { status: mediaStatus });
		}),
		...boardRoutes([]),
		downloadsRoute(),
		handshakeRoute(),
	);
}

/** The dialog opened on a tile and switched to its form. */
async function openTheForm(
	user: ReturnType<typeof userEvent.setup>,
	description: string,
) {
	await user.click(await screen.findByRole("img", { name: description }));
	await user.click(await screen.findByRole("button", { name: m.edit_pin() }));
	return screen.getByRole("dialog");
}

/** One of the image's three options, in the selector on the image side. */
function mediaChoice(dialog: HTMLElement, name: string) {
	return within(
		within(dialog).getByRole("radiogroup", { name: m.image() }),
	).getByRole("radio", { name });
}

/** The column's form, which holds the address unless the image is fetched from it. */
function theColumn(dialog: HTMLElement) {
	return within(dialog).getByRole("form", { name: m.pin_editing() });
}

describe("replace a pin's image with a file", () => {
	it("Given the file option and a file, Then the save applies it and the tile follows", async () => {
		const original = readyPin("a harbour at dusk", 800, 600);
		// The route supersedes in place, so the address the tile reads is unchanged: what says the
		// new bytes arrived is the shape the layout places the tile at.
		const replaced = {
			...original,
			media: mediaOf(original, { width: 400, height: 1000 }),
		};
		let held: Pin = original;
		const record = recorder();
		account(() => held, record, { reread: () => original });
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, original.description);
		// The image stays in view beside the form, and the choice is made on it (decision H).
		expect(
			within(dialog).getByRole("img", { name: original.description }),
		).toBeVisible();
		await user.click(mediaChoice(dialog, m.image_from_file()));
		await user.upload(
			within(dialog).getByLabelText(m.drop_image()),
			aPicture(),
		);
		// Chosen and not sent: nothing reaches the server until the pin is saved.
		expect(await within(dialog).findByText("harbour.png")).toBeVisible();
		expect(within(dialog).getByText(m.image_unsaved())).toBeVisible();
		expect(
			within(dialog).queryByRole("img", { name: original.description }),
		).toBeNull();
		expect(record.written).toEqual([]);

		held = replaced;
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		// The pin first, then the option chosen, on the route that supersedes atomically: no DELETE
		// precedes it (decision M).
		await waitFor(() => expect(record.written).toHaveLength(2));
		expect(record.written[1]).toEqual({ media: "multipart/form-data" });

		// The dialog is back to reading, and the grid behind it carries the image that just landed.
		await user.click(
			await within(dialog).findByRole("button", { name: m.close() }),
		);
		const tile = await screen.findByRole("img", { name: original.description });
		expect(tile).toHaveStyle({ aspectRatio: "400 / 1000" });
	});

	it("Given the address chosen, Then its field moves to the image and an empty one saves nothing", async () => {
		const found = readyPin("a harbour at dawn");
		// What the pin reads as while the server runs the download: the image it still has, and the
		// replacement beside it (property 8).
		const fetching = {
			...found,
			media: mediaOf(found, { replacement: { status: "PENDING" } }),
		};
		let held: Pin = found;
		const record = recorder();
		account(() => held, record, { reread: () => fetching });
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, found.description);
		expect(found.sourceMediaUrl).toBeNull();
		const address = { name: m.image_address() };
		await user.type(
			within(theColumn(dialog)).getByRole("textbox", address),
			FOUND_AT,
		);
		await user.click(mediaChoice(dialog, m.image_from_address()));

		// One field, in one place at a time: the selector's now, carrying what the column held.
		expect(
			within(theColumn(dialog)).queryByRole("textbox", address),
		).toBeNull();
		const field = within(dialog).getByRole("textbox", address);
		expect(field).toHaveValue(FOUND_AT);
		expect(within(dialog).getByText(m.image_fetched_on_save())).toBeVisible();
		// Emptied, it no longer falls back to keeping the image: there is nothing to save.
		await user.clear(field);
		expect(
			within(dialog).getByRole("button", { name: m.save() }),
		).toBeDisabled();
		held = fetching;
		// Enter saves from the image side as from the column: the field is still the form's.
		await user.type(field, `${FOUND_AT}{Enter}`);

		// The pin is written first, so the address the fetch reads is the one the pin now holds.
		await waitFor(() => expect(record.written).toHaveLength(2));
		expect(record.written).toEqual([
			{ pin: expect.objectContaining({ sourceMediaUrl: FOUND_AT }) },
			{ media: { sourceUrl: FOUND_AT } },
		]);
		// The form closes on the save, so the sub-state is what the next edit of that pin reads.
		await user.click(
			await within(dialog).findByRole("button", { name: m.edit_pin() }),
		);
		expect(await within(dialog).findByText(m.image_replacing())).toBeVisible();
	});

	it("Given the image kept and its address corrected, Then the save writes the pin and touches no image", async () => {
		const held = { ...readyPin("a harbour at noon"), sourceMediaUrl: FOUND_AT };
		const corrected = "https://example.test/harbour-at-noon.png";
		const record = recorder();
		account(() => held, record);
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, held.description);
		// Keeping is the default, and the address is then an ordinary field of the column.
		expect(mediaChoice(dialog, m.image_keep())).toBeChecked();
		const address = within(theColumn(dialog)).getByRole("textbox", {
			name: m.image_address(),
		});
		await user.clear(address);
		await user.type(address, corrected);
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		// Correcting the address fetches nothing: the form closes on the pin written alone.
		expect(
			await within(dialog).findByRole("button", { name: m.edit_pin() }),
		).toBeVisible();
		expect(record.written).toEqual([
			{ pin: expect.objectContaining({ sourceMediaUrl: corrected }) },
		]);
	});

	it("Given a failed download, Then the form shows why and leaves the fetch to the choice", async () => {
		const held = {
			...pin("a harbour in the dark", {
				status: "FAILED",
				reasonCode: "UNREACHABLE",
			}),
			sourceMediaUrl: FOUND_AT,
		};
		account(() => held, recorder());
		renderApp("/");
		const user = userEvent.setup();

		await user.click(await screen.findByText(m.reason_unreachable()));
		await user.click(await screen.findByRole("button", { name: m.edit_pin() }));
		const dialog = screen.getByRole("dialog");

		// One fetch control, on one address: the choice's, and never a Retry of the saved one.
		expect(within(dialog).getByText(m.reason_unreachable())).toBeVisible();
		expect(
			within(dialog).queryByRole("button", { name: m.retry() }),
		).toBeNull();
		await user.click(mediaChoice(dialog, m.image_from_address()));
		expect(
			within(dialog).queryByRole("button", { name: m.retry() }),
		).toBeNull();
	});

	it("Given the image refused after the pin was written, Then the form says which half failed", async () => {
		const held = {
			...readyPin("a harbour in the fog"),
			sourceMediaUrl: FOUND_AT,
		};
		const record = recorder();
		account(() => held, record, { mediaStatus: 500 });
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, held.description);
		await user.click(mediaChoice(dialog, m.image_from_address()));
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		// The fields are saved and the image is not, so the form says that and not that the save
		// failed whole.
		expect(await within(dialog).findByText(m.image_refused())).toBeVisible();
		expect(within(dialog).queryByText(m.pin_refused())).toBeNull();
		expect(
			within(dialog).getByRole("button", { name: m.save() }),
		).toBeVisible();
	});

	it("Given a file heavier than the deployment stores, Then it is never chosen at all", async () => {
		const held = readyPin("a harbour in the rain");
		const record = recorder();
		account(() => held, record);
		server.use(handshakeRoute({ maxImageBytes: 4 }));
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, held.description);
		await user.click(mediaChoice(dialog, m.image_from_file()));
		await user.upload(
			within(dialog).getByLabelText(m.drop_image()),
			new File(["more than four bytes"], "big.png", { type: "image/png" }),
		);

		// Judged at the choice and not at the save, and the refusal belongs to the gesture (ADR 0037).
		expect(await screen.findByRole("alert")).toHaveTextContent(
			m.file_too_heavy(),
		);
		expect(within(dialog).queryByText("big.png")).toBeNull();
		// Nothing to replace with, so the save that would silently keep the image is refused.
		expect(
			within(dialog).getByRole("button", { name: m.save() }),
		).toBeDisabled();
	});
});
