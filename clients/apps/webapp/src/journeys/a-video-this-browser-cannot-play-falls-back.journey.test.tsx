import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	downloadsRoute,
	handshakeRoute,
	pinsRoute,
	renderApp,
	sessionRoute,
	videoPin,
} from "../test/app";
import { server } from "../test/server";

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

/** The poster, the sentence and a link to the original, in place of a player. */
async function expectTheFallback(dialog: HTMLElement, pin: Pin) {
	const url = String(pin.media?.url);
	expect(await within(dialog).findByText(m.video_unplayable())).toBeVisible();
	expect(dialog.querySelector("video")).toBeNull();
	expect(
		within(dialog).getByRole("img", { name: pin.description }),
	).toHaveAttribute("src", `${url}?size=SMALL`);
	const link = within(dialog).getByRole("link", { name: m.video_download() });
	expect(link).toHaveAttribute("href", url);
}

afterEach(() => vi.restoreAllMocks());

describe("a video this browser cannot play falls back", () => {
	it("Given a type the browser rules out, Then the pin shows the fallback", async () => {
		const opened = videoPin(
			"a drone over the bay",
			'video/mp4; codecs="hvc1.1.6.L93.B0"',
		);
		const asked = vi
			.spyOn(HTMLMediaElement.prototype, "canPlayType")
			.mockReturnValue("");

		const dialog = await openThe(opened);

		expect(asked).toHaveBeenCalledWith(opened.media?.mimeType);
		await expectTheFallback(dialog, opened);
	});

	it("Given a video that fails while it loads, Then the pin shows the fallback", async () => {
		const opened = videoPin("a drone over the bay");
		const dialog = await openThe(opened);
		const video = dialog.querySelector("video");
		if (video === null) {
			throw new Error("No video was mounted.");
		}

		fireEvent.error(video);

		await expectTheFallback(dialog, opened);
	});
});
