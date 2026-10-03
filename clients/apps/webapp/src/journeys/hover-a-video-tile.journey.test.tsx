import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
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

describe("hover a video tile", () => {
	it("Given a video tile and an image tile, Then only the video carries the play icon", async () => {
		server.use(
			sessionRoute(() => true),
			pinsRoute([[videoPin("waves on the pier"), readyPin("a cat asleep")]]),
			downloadsRoute(),
			handshakeRoute(),
		);

		renderApp("/");

		const video = await screen.findByRole("row", { name: "waves on the pier" });
		const image = screen.getByRole("row", { name: "a cat asleep" });
		expect(
			within(video).getByRole("img", { name: m.video_badge() }),
		).toBeVisible();
		expect(
			within(image).queryByRole("img", { name: m.video_badge() }),
		).toBeNull();
	});

	it("Given a video tile, Then hovering plays the original muted and leaving stops it", async () => {
		const hovered = videoPin("waves on the pier");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[hovered]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: hovered.description });
		const still = within(tile).getByRole("img", { name: hovered.description });
		const user = userEvent.setup();
		expect(tile.querySelector("video")).toBeNull();

		await user.hover(still);

		const url = String(hovered.media?.url);
		const video = tile.querySelector("video");
		expect(video).toHaveAttribute("src", url);
		expect(video?.muted).toBe(true);
		expect(video?.loop).toBe(true);
		await user.unhover(still);
		expect(tile.querySelector("video")).toBeNull();
	});

	it("Given a touch on a video tile, Then the original is not fetched", async () => {
		const touched = videoPin("waves on the pier");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[touched]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: touched.description });

		fireEvent.pointerOver(
			within(tile).getByRole("img", { name: touched.description }),
			{ pointerType: "touch" },
		);

		expect(tile.querySelector("video")).toBeNull();
	});
});
