import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	downloadsRoute,
	handshakeRoute,
	pinsRoute,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** The spinner's delay in `PinGrid.tsx`, which a test outwaits to show it never came. */
const SPINNER_DELAY_MS = 300;

async function openThe(pin: Pin) {
	server.use(
		sessionRoute(() => true),
		pinsRoute([[pin]]),
		downloadsRoute(),
		handshakeRoute(),
	);
	renderApp("/");
	await userEvent
		.setup()
		.click(await screen.findByRole("img", { name: pin.description }));
	return screen.findByRole("dialog");
}

afterEach(() => vi.restoreAllMocks());

describe("open a pin", () => {
	it("Given a tile, Then the pin opens with what the API knows of it", async () => {
		const boardId = "5e0d2a52-6f7c-4f5e-9c2a-8b3e0d1a7c44";
		const opened = {
			...readyPin("a harbour at dusk"),
			sourceContextUrl: "https://photos.example.test/harbours/dusk",
			tags: [{ name: "harbours" }],
			boards: [{ id: boardId, name: "Evenings" }],
		};
		server.use(
			sessionRoute(() => true),
			pinsRoute([[opened]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: opened.description }),
		);

		const dialog = await screen.findByRole("dialog");
		// The original, under the rendition the grid drew, which a column jsdom measures at 0 px makes the small one.
		const image = within(dialog).getByRole("img", { name: opened.description });
		expect(image).toHaveAttribute("src", `/api/v1/pins/${opened.id}/image`);
		expect(dialog.querySelector('img[alt=""]')).toHaveAttribute(
			"src",
			`/api/v1/pins/${opened.id}/image?size=SMALL`,
		);
		// The page the pin was found on, named by its host and opened beside the application.
		const source = within(dialog).getByRole("link", {
			name: "photos.example.test",
		});
		expect(source).toHaveAttribute("href", opened.sourceContextUrl);
		expect(source).toHaveAttribute("target", "_blank");
		expect(within(dialog).getByText("harbours")).toBeVisible();
		expect(
			within(dialog).getByRole("link", { name: "Evenings" }),
		).toHaveAttribute("href", `/boards/${boardId}`);
	});

	it("Given a grid drawn at the medium rendition, Then the pin loads under that one", async () => {
		const opened = readyPin("a harbour at dusk");
		// A small rendition under 0 px is what makes a column jsdom measures at 0 px ask for the medium one.
		server.use(
			sessionRoute(() => true),
			pinsRoute([[opened]]),
			downloadsRoute(),
			handshakeRoute({ small: -1 }),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: opened.description }),
		);

		const dialog = await screen.findByRole("dialog");
		expect(dialog.querySelector('img[alt=""]')).toHaveAttribute(
			"src",
			`/api/v1/pins/${opened.id}/image?size=MEDIUM`,
		);
	});

	it("Given an original still decoding, Then the grid's rendition stays and a spinner says so", async () => {
		let decoded = () => {};
		vi.spyOn(HTMLImageElement.prototype, "decode").mockReturnValue(
			new Promise<void>((resolve) => {
				decoded = resolve;
			}),
		);
		const dialog = await openThe(readyPin("a harbour at dusk"));

		const loading = await within(dialog).findByRole("status", {
			name: m.image_original_loading(),
		});
		expect(dialog.querySelector('img[alt=""]')).not.toBeNull();
		decoded();

		// Swapped once decoded rather than loaded, so no frame shows neither.
		await waitFor(() => expect(loading).not.toBeInTheDocument());
		expect(dialog.querySelector('img[alt=""]')).toBeNull();
	});

	it("Given an original decoded at once, Then no spinner ever shows", async () => {
		vi.spyOn(HTMLImageElement.prototype, "decode").mockResolvedValue();
		let shown = false;
		const watch = new MutationObserver(() => {
			shown ||=
				screen.queryByRole("status", { name: m.image_original_loading() }) !==
				null;
		});
		watch.observe(document.body, { childList: true, subtree: true });

		const dialog = await openThe(readyPin("a harbour at dusk"));
		await new Promise((resolve) => setTimeout(resolve, 2 * SPINNER_DELAY_MS));
		watch.disconnect();

		expect(shown).toBe(false);
		expect(dialog.querySelector('img[alt=""]')).toBeNull();
	});

	it("Given an original that fails to decode, Then the grid's rendition stays without a spinner", async () => {
		vi.spyOn(HTMLImageElement.prototype, "decode").mockRejectedValue(
			new DOMException("", "EncodingError"),
		);
		const dialog = await openThe(readyPin("a harbour at dusk"));
		await new Promise((resolve) => setTimeout(resolve, 2 * SPINNER_DELAY_MS));

		expect(
			within(dialog).queryByRole("status", {
				name: m.image_original_loading(),
			}),
		).toBeNull();
		expect(dialog.querySelector('img[alt=""]')).not.toBeNull();
	});

	it("Given an open pin, Then closing it returns to the grid", async () => {
		const opened = readyPin("a harbour at dusk");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[opened]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();

		await user.click(
			await screen.findByRole("img", { name: opened.description }),
		);
		await user.click(await screen.findByRole("button", { name: m.close() }));

		expect(screen.queryByRole("dialog")).toBeNull();
	});
});
