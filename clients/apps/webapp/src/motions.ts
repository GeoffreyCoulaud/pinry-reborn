import { type QueryClient, useQuery } from "@tanstack/react-query";
import { frameMillis } from "./lib/clock";
import { isVideo } from "./lib/media";
import type { Pin } from "./pins";

/** The stored image types that can move: libvips reads no APNG animation. */
const ANIMATABLE = new Set(["image/gif", "image/webp"]);

const ANIMATION = "animation";

/** An animated image decoded from its original's bytes, its frames' durations in ms. */
interface Animation {
	decoder: ImageDecoder;
	frames: number[];
}

/** How a version moves on the stage: not at all, under the shared clock, or on its own. */
export type Motion =
	| { kind: "still" }
	| { kind: "pending" }
	| { kind: "own" }
	| { kind: "video"; duration: number }
	| { kind: "animation"; duration: number; animation: Animation };

/** The original's frames, or `null` for a still image. */
async function decode(url: string, type: string): Promise<Animation | null> {
	const answer = await fetch(url);
	if (!answer.ok) {
		throw new Error(`The original answered ${answer.status}.`);
	}
	const decoder = new ImageDecoder({ data: await answer.arrayBuffer(), type });
	// `frameCount` is final once every byte is buffered (MDN, `ImageDecoder`).
	await decoder.tracks.ready;
	await decoder.completed;
	const track = decoder.tracks.selectedTrack;
	if (!track?.animated) {
		decoder.close();
		return null;
	}
	const micros: (number | null)[] = [];
	for (let frameIndex = 0; frameIndex < track.frameCount; frameIndex++) {
		const { image } = await decoder.decode({ frameIndex });
		micros.push(image.duration);
		image.close();
	}
	return { decoder, frames: frameMillis(micros) };
}

/** Closes an animated image's decoder, and frees its bytes, once the cache drops it. */
export function closeDroppedAnimations(client: QueryClient): void {
	client.getQueryCache().subscribe((event) => {
		if (event.type === "removed" && event.query.queryKey[0] === ANIMATION) {
			(event.query.state.data as Animation | null | undefined)?.decoder.close();
		}
	});
}

/** A video on its contract's duration; a GIF or WebP decoded frame by frame where `ImageDecoder` exists. */
export function useMotion(version: Pin): Motion {
	const media = version.media;
	const url = media?.url;
	const type = media?.mimeType;
	const animatable = type != null && ANIMATABLE.has(type.toLowerCase());
	const decodable =
		url != null && animatable && typeof ImageDecoder !== "undefined";
	const animation = useQuery({
		queryKey: [ANIMATION, url, type],
		queryFn: () => decode(String(url), String(type)),
		enabled: decodable,
		staleTime: Number.POSITIVE_INFINITY,
		retry: false,
	});

	if (isVideo(type)) {
		return media?.durationMillis == null
			? { kind: "own" }
			: { kind: "video", duration: media.durationMillis };
	}
	if (!animatable) {
		return { kind: "still" };
	}
	if (!decodable || animation.isError) {
		return { kind: "own" };
	}
	if (animation.data === undefined) {
		return { kind: "pending" };
	}
	return animation.data === null
		? { kind: "still" }
		: {
				kind: "animation",
				duration: animation.data.frames.reduce((sum, one) => sum + one, 0),
				animation: animation.data,
			};
}
