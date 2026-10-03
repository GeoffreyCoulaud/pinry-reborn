import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	download,
	downloadsPage,
	downloadsRoute,
	handshakeRoute,
	pinsRoute,
	renderApp,
	sessionRoute,
	videoPin,
} from "../test/app";
import { server } from "../test/server";

async function openThe(pin: Pin, downloads = downloadsRoute()) {
	server.use(
		sessionRoute(() => true),
		pinsRoute([[pin]]),
		downloads,
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

	it("Given the video replaced by a playable one while its pin is open, Then the new one gets a player", async () => {
		const opened = videoPin(
			"a drone over the bay",
			'video/mp4; codecs="hvc1.1.6.L93.B0"',
		);
		// The address stays the pin's own across a replacement: only the stored type and size change.
		const replaced = {
			...opened,
			media: {
				...opened.media,
				status: "READY",
				mimeType: 'video/mp4; codecs="avc1.64001F, mp4a.40.2"',
				byteSize: 2,
			},
		} satisfies Pin;
		vi.spyOn(HTMLMediaElement.prototype, "canPlayType").mockImplementation(
			(type) => (type.includes("hvc1") ? "" : "maybe"),
		);
		// The replacing download runs on the first poll and has settled by the next.
		let polls = 0;
		server.use(
			http.get("/api/v1/pins/:pinId", () => HttpResponse.json(replaced)),
		);

		const dialog = await openThe(
			opened,
			http.get("/api/v1/me/media-downloads", () => {
				polls += 1;
				return HttpResponse.json(
					downloadsPage(polls > 1 ? [] : [download(opened.id, "PENDING")]),
				);
			}),
		);
		await expectTheFallback(dialog, opened);

		await waitFor(
			() =>
				expect(dialog.querySelector("video")).toHaveAttribute(
					"src",
					String(opened.media?.url),
				),
			{ timeout: 4000 },
		);
		expect(within(dialog).queryByText(m.video_unplayable())).toBeNull();
	}, 15_000);
});
