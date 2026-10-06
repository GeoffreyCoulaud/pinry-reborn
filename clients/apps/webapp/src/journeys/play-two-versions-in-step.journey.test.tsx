import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	downloadsRoute,
	duplicateRoutes,
	handshakeRoute,
	onePinPage,
	readyPin,
	renderApp,
	sessionRoute,
	videoPin,
} from "../test/app";
import { server } from "../test/server";

const lasting = (pin: Pin, durationMillis: number): Pin => ({
	...pin,
	media: pin.media && { ...pin.media, durationMillis },
	hasPendingDuplicates: true,
});

const animated = (description: string, width = 800, height = 600): Pin => {
	const bare = readyPin(description, width, height);
	return {
		...bare,
		media: bare.media && { ...bare.media, mimeType: "image/gif" },
		hasPendingDuplicates: true,
	};
};

/** The stage of the comparator opened on a pin and one pending candidate, the candidate under review. */
async function stageOf(open: Pin, candidate: Pin) {
	server.use(
		sessionRoute(() => true),
		onePinPage(() => [open, candidate]),
		...duplicateRoutes([{ pins: [open, candidate], rejected: false }]),
		downloadsRoute(),
		handshakeRoute(),
	);
	renderApp("/");
	const user = userEvent.setup();
	await user.click(await screen.findByRole("img", { name: open.description }));
	const dialog = await screen.findByRole("dialog");
	await user.click(
		await within(dialog).findByRole("button", { name: m.compare() }),
	);
	const stage = await within(dialog).findByRole("figure", {
		name: `${candidate.description} · ${open.description}`,
	});
	return { user, dialog, stage };
}

describe("play two versions in step", () => {
	it("Given two videos of 4 and 2.4 seconds, Then one bar plays both over 4 seconds, muted, and the shorter moves by up to 1.6 seconds", async () => {
		const open = lasting(videoPin("waves on the pier"), 4_000);
		const shorter = lasting(videoPin("waves on the pier, cut"), 2_400);

		const { user, dialog, stage } = await stageOf(open, shorter);

		const videos = [...stage.querySelectorAll("video")];
		expect(videos.map((video) => video.getAttribute("src"))).toEqual([
			open.media?.url,
			shorter.media?.url,
		]);
		for (const video of videos) {
			expect(video.muted).toBe(true);
			expect(video).not.toHaveAttribute("controls");
		}
		expect(
			within(dialog).getByRole("button", { name: m.compare_play() }),
		).toBeEnabled();
		const position = within(dialog).getByRole("slider", {
			name: m.compare_position(),
		});
		expect(position).toHaveAttribute("max", "4000");
		const offset = within(dialog).getByRole("slider", {
			name: m.compare_offset(),
		});
		expect(offset).toHaveAttribute("max", "1600");

		await user.click(offset);
		await user.keyboard("{ArrowRight}");

		expect(offset).toHaveValue("100");
		expect(
			within(
				within(dialog).getByRole("list", { name: m.compare_versions() }),
			).getByRole("button", { name: shorter.description }),
		).toHaveAttribute("aria-current", "true");
	});

	it("Given two animated images and no ImageDecoder, Then each plays on its own as an image and there is no bar", async () => {
		const { dialog, stage } = await stageOf(
			animated("a cat turning"),
			animated("a cat turning, slower"),
		);

		expect(stage.querySelectorAll("img")).toHaveLength(2);
		expect(within(dialog).queryByRole("slider")).toBeNull();
		expect(
			within(dialog).queryByRole("button", { name: m.compare_play() }),
		).toBeNull();
	});

	it("Given a video beside an animated image and no ImageDecoder, Then the video keeps its own controls, the image plays on its own, and there is no bar", async () => {
		const { dialog, stage } = await stageOf(
			lasting(videoPin("a cat turning, filmed"), 3_000),
			animated("a cat turning", 400, 300),
		);

		expect(stage.querySelector("video")).toHaveAttribute("controls");
		expect(stage.querySelectorAll("img")).toHaveLength(1);
		expect(within(dialog).queryByRole("slider")).toBeNull();
	});
});
