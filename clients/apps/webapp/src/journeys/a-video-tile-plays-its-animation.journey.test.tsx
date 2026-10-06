import { fireEvent, screen, within } from "@testing-library/react";
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

describe("a video tile plays its animation", () => {
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

	it("Given a video tile and an image tile, Then the video shows its animated rendition and the image its still, untouched", async () => {
		const video = videoPin("waves on the pier");
		const image = readyPin("a cat asleep");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[video, image]]),
			downloadsRoute(),
			handshakeRoute(),
		);

		renderApp("/");

		const videoTile = await screen.findByRole("row", {
			name: video.description,
		});
		const videoMedia = within(videoTile).getByRole("img", {
			name: video.description,
		});
		expect(videoMedia).toHaveAttribute(
			"src",
			`${String(video.media?.url)}?size=SMALL&animated=true`,
		);
		expect(videoTile.querySelector("video")).toBeNull();
		const imageTile = screen.getByRole("row", { name: image.description });
		expect(
			within(imageTile).getByRole("img", { name: image.description }),
		).toHaveAttribute("src", `${String(image.media?.url)}?size=SMALL`);
	});

	it("Given an animated rendition the server cannot draw, Then the video tile says so", async () => {
		const video = videoPin("waves on the pier");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[video]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: video.description });

		fireEvent.error(within(tile).getByRole("img", { name: video.description }));

		expect(
			within(tile).getByRole("img", { name: m.preview_unavailable() }),
		).toBeVisible();
	});
});
