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

	it("Given a video tile, Then hovering shows its animated rendition and leaving brings the still back", async () => {
		const hovered = videoPin("waves on the pier");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[hovered]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: hovered.description });
		const media = within(tile).getByRole("img", { name: hovered.description });
		const user = userEvent.setup();
		const url = String(hovered.media?.url);

		await user.hover(media);

		expect(media).toHaveAttribute("src", `${url}?size=SMALL&animated=true`);
		expect(tile.querySelector("video")).toBeNull();
		await user.unhover(media);
		expect(media).toHaveAttribute("src", `${url}?size=SMALL`);
	});

	it("Given an animated rendition the server cannot draw, Then the tile says so and leaving brings the still back", async () => {
		const hovered = videoPin("waves on the pier");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[hovered]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: hovered.description });
		const user = userEvent.setup();

		await user.hover(
			within(tile).getByRole("img", { name: hovered.description }),
		);
		fireEvent.error(
			within(tile).getByRole("img", { name: hovered.description }),
		);

		const unavailable = within(tile).getByRole("img", {
			name: m.preview_unavailable(),
		});
		expect(unavailable).toBeVisible();
		// user-event leaves from the image it last entered, which has left the tree since.
		await user.hover(unavailable);
		await user.unhover(unavailable);
		expect(
			within(tile).getByRole("img", { name: hovered.description }),
		).toHaveAttribute("src", `${String(hovered.media?.url)}?size=SMALL`);
	});

	it("Given an image tile, Then hovering keeps its still", async () => {
		const hovered = readyPin("a cat asleep");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[hovered]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: hovered.description });
		const media = within(tile).getByRole("img", { name: hovered.description });

		await userEvent.setup().hover(media);

		expect(media).toHaveAttribute(
			"src",
			`${String(hovered.media?.url)}?size=SMALL`,
		);
	});

	it("Given a touch on a video tile, Then its still stays and nothing animated is fetched", async () => {
		const touched = videoPin("waves on the pier");
		server.use(
			sessionRoute(() => true),
			pinsRoute([[touched]]),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const tile = await screen.findByRole("row", { name: touched.description });
		const media = within(tile).getByRole("img", { name: touched.description });

		fireEvent.pointerOver(media, { pointerType: "touch" });

		expect(media).toHaveAttribute(
			"src",
			`${String(touched.media?.url)}?size=SMALL`,
		);
	});
});
