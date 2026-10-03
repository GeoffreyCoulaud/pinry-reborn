import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	downloadsRoute,
	handshakeRoute,
	pinsRoute,
	readyPin,
	renderApp,
	sessionRoute,
	videoPin,
} from "../test/app";
import { server } from "../test/server";

/** The grid on one page of pins, with the first of them open. */
async function openTheFirst(pins: Pin[]) {
	server.use(
		sessionRoute(() => true),
		pinsRoute([pins]),
		downloadsRoute(),
		handshakeRoute(),
	);
	renderApp("/");
	await userEvent
		.setup()
		.click(await screen.findByRole("img", { name: pins[0]?.description }));
	const dialog = await screen.findByRole("dialog");
	return { dialog, video: dialog.querySelector("video") };
}

describe("play a video pin", () => {
	it("Given a video pin, Then its view plays the original under the grid's rendition", async () => {
		const opened = videoPin("waves on the pier");

		const { dialog, video } = await openTheFirst([opened]);

		const url = String(opened.media?.url);
		expect(video).toHaveAttribute("src", url);
		// The grid's rendition, which a column jsdom measures at 0 px makes the small one.
		expect(video).toHaveAttribute("poster", `${url}?size=SMALL`);
		expect(video).toHaveAttribute("controls");
		expect(within(dialog).queryByText(m.video_unplayable())).toBeNull();
	});

	it("Given a video stored with no dimensions, Then it plays in a square box and never as an image", async () => {
		const bare = videoPin("waves at night");
		const opened = {
			...bare,
			media: { ...bare.media, status: "READY", width: null, height: null },
		} satisfies Pin;

		const { dialog, video } = await openTheFirst([opened]);

		expect(video).toHaveAttribute("src", String(opened.media.url));
		expect(video).toHaveStyle({ aspectRatio: "1 / 1" });
		expect(
			within(dialog).queryByRole("img", { name: opened.description }),
		).toBeNull();
	});

	it("Given the video, Then its own arrows and a touch drag on it leave the pin open", async () => {
		const pins = [videoPin("waves on the pier"), readyPin("a cat asleep")];
		const { dialog, video } = await openTheFirst(pins);
		if (video === null) {
			throw new Error("No video was mounted.");
		}

		// The controls seek on the arrows and on a drag along the timeline.
		fireEvent.keyDown(video, { key: "ArrowRight" });
		expect(dialog).toHaveAccessibleName("waves on the pier");
		const at = (clientX: number) => ({
			pointerId: 1,
			pointerType: "touch",
			clientX,
			clientY: 300,
		});
		fireEvent.pointerDown(video, at(300));
		fireEvent.pointerMove(video, at(200));
		fireEvent.pointerMove(video, at(100));
		fireEvent.pointerUp(video, at(100));

		expect(dialog).toHaveAccessibleName("waves on the pier");
	});
});
