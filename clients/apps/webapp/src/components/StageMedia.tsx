import { useEffect, useRef } from "react";
import { frameAt } from "../lib/clock";
import { isVideo } from "../lib/media";
import type { Motion } from "../motions";

/** How far a playing video may stray from the clock before it is sought back. */
const DRIFT_MS = 150;

const MEDIA = "pointer-events-none size-full object-contain";

/** A video the clock seeks while it holds and plays while it moves, muted. */
function ClockedVideo({
	url,
	time,
	playing,
}: {
	url: string;
	time: number;
	playing: boolean;
}) {
	const video = useRef<HTMLVideoElement>(null);
	const previous = useRef(time);
	useEffect(() => {
		const element = video.current;
		if (element === null) {
			return;
		}
		// Held on its first or last frame, its time stands still while the clock runs.
		const moving = playing && time > previous.current;
		previous.current = time;
		const drift = Math.abs(element.currentTime * 1_000 - time);
		if (moving) {
			if (drift > DRIFT_MS) {
				element.currentTime = time / 1_000;
			}
			if (element.paused) {
				// A pause before playback starts rejects it, which the next tick settles.
				element.play().catch(() => undefined);
			}
			return;
		}
		if (!element.paused) {
			element.pause();
		}
		if (drift > 1) {
			element.currentTime = time / 1_000;
		}
	}, [time, playing]);
	return (
		<video
			ref={video}
			src={url}
			muted
			playsInline
			preload="auto"
			className={MEDIA}
		/>
	);
}

/** An animated image's frame at the clock's time, drawn on a canvas of the frame's own size. */
function ClockedAnimation({
	motion,
	time,
}: {
	motion: Extract<Motion, { kind: "animation" }>;
	time: number;
}) {
	const canvas = useRef<HTMLCanvasElement>(null);
	const { decoder, frames } = motion.animation;
	const frameIndex = frameAt(frames, time);
	useEffect(() => {
		let shown = true;
		decoder.decode({ frameIndex }).then(
			({ image }) => {
				const element = canvas.current;
				if (shown && element !== null) {
					element.width = image.displayWidth;
					element.height = image.displayHeight;
					element.getContext("2d")?.drawImage(image, 0, 0);
				}
				image.close();
			},
			// A frame that fails to decode leaves the previous one drawn.
			() => undefined,
		);
		return () => {
			shown = false;
		};
	}, [decoder, frameIndex]);
	return <canvas ref={canvas} className={MEDIA} />;
}

/** A version on the stage: under the clock when the pair has one, on its own otherwise. */
export function StageMedia({
	url,
	mimeType,
	motion,
	clocked,
	time,
	playing,
}: {
	url: string;
	mimeType?: string | null;
	motion: Motion;
	clocked: boolean;
	time: number;
	playing: boolean;
}) {
	if (isVideo(mimeType)) {
		// Beside a version the clock cannot hold, a video keeps its own controls.
		return clocked ? (
			<ClockedVideo url={url} time={time} playing={playing} />
		) : (
			<video
				src={url}
				muted
				controls
				playsInline
				data-stage-control
				className="size-full object-contain"
			/>
		);
	}
	if (clocked && motion.kind === "animation") {
		return <ClockedAnimation motion={motion} time={time} />;
	}
	return <img src={url} alt="" draggable={false} className={MEDIA} />;
}
