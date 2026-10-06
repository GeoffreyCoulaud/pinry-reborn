import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
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

afterEach(() => {
	vi.unstubAllGlobals();
	vi.restoreAllMocks();
});

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

/** `ImageDecoder` over the pin id the media route answers, a single frame being still; the pins decoded and drawn. */
function stubImageDecoder(frames: Record<string, (number | null)[]>) {
	const decoded: string[] = [];
	const drawn = new Map<string, number>();
	vi.stubGlobal(
		"ImageDecoder",
		class {
			readonly pin: string;
			readonly completed = Promise.resolve();
			readonly tracks;
			constructor({ data }: { data: ArrayBuffer }) {
				this.pin = new TextDecoder().decode(data);
				decoded.push(this.pin);
				const frameCount = frames[this.pin]?.length ?? 0;
				this.tracks = {
					ready: Promise.resolve(),
					selectedTrack: { animated: frameCount > 1, frameCount },
				};
			}
			decode({ frameIndex }: { frameIndex: number }) {
				const image = {
					pin: this.pin,
					frameIndex,
					duration: frames[this.pin]?.[frameIndex] ?? null,
					displayWidth: 1,
					displayHeight: 1,
					close: () => {},
				};
				return Promise.resolve({ image, complete: true });
			}
			close() {}
		},
	);
	vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
		drawImage: (image: { pin: string; frameIndex: number }) =>
			drawn.set(image.pin, image.frameIndex),
	} as unknown as CanvasRenderingContext2D);
	server.use(
		http.get("/api/v1/pins/:pinId/media", ({ params }) =>
			HttpResponse.text(String(params.pinId)),
		),
	);
	return { decoded, drawn };
}

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
	it("Given two videos of 4 and 2.4 seconds, Then one bar plays both over 4 seconds, muted, and the shorter moves by up to 1.6 seconds, 10 ms at a step", async () => {
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

		expect(offset).toHaveValue("10");
		expect(within(dialog).getByText("0.01s")).toBeInTheDocument();
		expect(
			within(
				within(dialog).getByRole("list", { name: m.compare_versions() }),
			).getByRole("button", { name: shorter.description }),
		).toHaveAttribute("aria-current", "true");
	});

	it("Given two animated images and ImageDecoder, Then one bar plays both over the longer's frames, each drawn at the frame the time names", async () => {
		const open = animated("a cat turning");
		const slower = animated("a cat turning, slower");
		// Microseconds per frame, as `VideoFrame.duration` states them: none and zero count 100 ms.
		const { drawn } = stubImageDecoder({
			[open.id]: [1_000_000, 1_000_000, null],
			[slower.id]: [500_000, 0, 1_000_000],
		});

		const { user, dialog, stage } = await stageOf(open, slower);

		const position = await within(dialog).findByRole("slider", {
			name: m.compare_position(),
		});
		expect(position).toHaveAttribute("max", "2100");
		expect(
			within(dialog).getByRole("slider", { name: m.compare_offset() }),
		).toHaveAttribute("max", "500");
		expect(stage.querySelectorAll("canvas")).toHaveLength(2);
		expect(stage.querySelector("img")).toBeNull();
		expect(within(dialog).getByText("1.6 sec")).toBeInTheDocument();
		await waitFor(() => {
			expect(drawn.get(open.id)).toBe(0);
			expect(drawn.get(slower.id)).toBe(0);
		});

		fireEvent.change(position, { target: { value: "550" } });

		// The shorter's frame stating zero lasts from 500 to 600 ms.
		await waitFor(() => {
			expect(drawn.get(open.id)).toBe(0);
			expect(drawn.get(slower.id)).toBe(1);
		});

		await user.click(position);
		await user.keyboard("{End}");

		await waitFor(() => {
			expect(drawn.get(open.id)).toBe(2);
			expect(drawn.get(slower.id)).toBe(2);
		});
	});

	it("Given a video beside an animated image and ImageDecoder, Then one bar plays both, the video without its controls and the image on a canvas", async () => {
		const image = animated("a cat turning", 400, 300);
		stubImageDecoder({ [image.id]: [1_000_000, 1_000_000] });

		const { dialog, stage } = await stageOf(
			lasting(videoPin("a cat turning, filmed"), 3_000),
			image,
		);

		const position = await within(dialog).findByRole("slider", {
			name: m.compare_position(),
		});
		expect(position).toHaveAttribute("max", "3000");
		expect(
			within(dialog).getAllByRole("button", { name: m.compare_play() }),
		).toHaveLength(1);
		expect(stage.querySelector("video")).not.toHaveAttribute("controls");
		expect(stage.querySelectorAll("canvas")).toHaveLength(1);
		expect(stage.querySelector("img")).toBeNull();
	});

	it("Given a JPEG beside a GIF of one frame and ImageDecoder, Then the GIF alone is decoded, both show as images, and there is no bar", async () => {
		const jpeg = readyPin("a still harbour");
		const open = {
			...jpeg,
			media: jpeg.media && { ...jpeg.media, mimeType: "image/jpeg" },
		};
		const gif = animated("the same harbour, as a GIF", 400, 300);
		const { decoded } = stubImageDecoder({ [gif.id]: [null] });
		// Both versions' fetches start in one commit, before either is decoded.
		const fetched = vi.spyOn(globalThis, "fetch");

		const { dialog, stage } = await stageOf(open, gif);

		await waitFor(() => expect(decoded).toEqual([gif.id]));
		const urls = fetched.mock.calls.map(([input]) => String(input));
		expect(urls).not.toContain(open.media?.url);
		expect(stage.querySelectorAll("img")).toHaveLength(2);
		expect(stage.querySelector("canvas")).toBeNull();
		expect(within(dialog).queryByRole("slider")).toBeNull();
		expect(
			within(dialog).queryByRole("button", { name: m.compare_play() }),
		).toBeNull();
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
