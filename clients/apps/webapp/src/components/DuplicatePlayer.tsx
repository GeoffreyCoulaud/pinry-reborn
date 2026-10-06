import { Slider } from "@heroui/react";
import { Pause, Play } from "lucide-react";
import { type ComponentProps, useEffect, useState } from "react";
import { advance, localTimes, slackOf } from "../lib/clock";
import { type Motion, useMotion } from "../motions";
import { m } from "../paraglide/messages.js";
import { getLocale } from "../paraglide/runtime.js";
import type { Pin } from "../pins";
import { DuplicateStage } from "./DuplicateStage";
import { IconButton } from "./IconButton";
import { StageMedia } from "./StageMedia";

/** The shared time in ms, moving by the frame while playing and back to 0 at `length`. */
function useClock(length: number) {
	const [time, setTime] = useState(0);
	const [playing, setPlaying] = useState(false);
	useEffect(() => {
		if (!playing) {
			return;
		}
		let last = performance.now();
		let frame = requestAnimationFrame(function tick(now) {
			// Read before the update runs: React calls it later, `last` moved on by then.
			const elapsed = now - last;
			setTime((current) => advance(current, elapsed, length));
			last = now;
			frame = requestAnimationFrame(tick);
		});
		return () => cancelAnimationFrame(frame);
	}, [playing, length]);
	return { time, setTime, playing, setPlaying };
}

function isTimed(
	motion: Motion,
): motion is Extract<Motion, { kind: "video" | "animation" }> {
	return motion.kind === "video" || motion.kind === "animation";
}

/** One play and pause, the position over the longer duration, and the shorter's offset when it can move. */
function PlayerBar({
	clock: { time, setTime, playing, setPlaying },
	durations,
	offset,
	setOffset,
}: {
	clock: ReturnType<typeof useClock>;
	durations: number[];
	offset: number;
	setOffset: (offset: number) => void;
}) {
	const length = Math.max(...durations);
	const slack = slackOf(durations);
	const tenths = { minimumFractionDigits: 1, maximumFractionDigits: 1 };
	const number = new Intl.NumberFormat(getLocale(), tenths);
	const seconds = new Intl.NumberFormat(getLocale(), {
		...tenths,
		style: "unit",
		unit: "second",
		unitDisplay: "narrow",
	});
	const label = (millis: number) => seconds.format(millis / 1_000);
	const total = `${number.format(length / 1_000)} / ${label(length)}`;
	// One label width on both rows, so the slider ends where the bar ends; no wider, for a phone's bar.
	const box = {
		className: "shrink-0 text-end text-sm tabular-nums text-muted",
		style: { width: `${total.length}ch` },
	};
	return (
		<div className="flex flex-col gap-1">
			<div className="flex items-center gap-3">
				<IconButton
					icon={playing ? Pause : Play}
					name={playing ? m.compare_pause() : m.compare_play()}
					size="sm"
					variant="secondary"
					onPress={() => setPlaying(!playing)}
				/>
				<Slider
					aria-label={m.compare_position()}
					maxValue={length}
					value={time}
					onChange={(value) => setTime(Number(value))}
					className="flex-1"
				>
					{/* No fill on the position, its start cap included. */}
					<Slider.Track className="border-s-transparent">
						{slack > 0 ? (
							<div
								className="absolute inset-y-0 rounded-full bg-accent/35"
								style={{
									left: `${(offset / length) * 100}%`,
									width: `${(Math.min(...durations) / length) * 100}%`,
								}}
							/>
						) : null}
						<Slider.Thumb />
					</Slider.Track>
				</Slider>
				<span {...box}>
					{number.format(time / 1_000)} / {label(length)}
				</span>
			</div>
			{slack > 0 ? (
				<div className="flex items-center gap-3">
					<span className="text-sm text-muted">{m.compare_offset()}</span>
					<Slider
						aria-label={m.compare_offset()}
						maxValue={slack}
						step={100}
						value={offset}
						onChange={(value) => setOffset(Number(value))}
						className="flex-1"
					>
						<Slider.Track>
							<Slider.Fill />
							<Slider.Thumb />
						</Slider.Track>
					</Slider>
					<span {...box}>{label(offset)}</span>
				</div>
			) : null}
		</div>
	);
}

/** The stage with both versions played in step under one clock, the shorter moved by `offset` (decision D). */
export function DuplicatePlayer({
	offset,
	setOffset,
	...stage
}: Omit<ComponentProps<typeof DuplicateStage>, "media"> & {
	offset: number;
	setOffset: (offset: number) => void;
}) {
	const underMotion = useMotion(stage.under);
	const keptMotion = useMotion(stage.kept);
	const motions =
		stage.under.id === stage.kept.id
			? [underMotion]
			: [underMotion, keptMotion];
	// One version the clock cannot hold, or not yet, and every version plays on its own.
	const clocked = motions.every(
		(one) => one.kind !== "own" && one.kind !== "pending",
	);
	const timed = clocked ? motions.filter(isTimed) : [];
	const durations = timed.map((one) => one.duration);
	const clock = useClock(Math.max(0, ...durations));
	const times = localTimes(clock.time, offset, durations);
	const timeOf = new Map<Motion, number>(
		timed.map((one, at) => [one, times[at] ?? 0]),
	);
	const media = (version: Pin) => {
		const motion = version.id === stage.under.id ? underMotion : keptMotion;
		return version.media?.url ? (
			<StageMedia
				url={version.media.url}
				mimeType={version.media.mimeType}
				motion={motion}
				clocked={clocked}
				time={timeOf.get(motion) ?? 0}
				playing={clock.playing}
			/>
		) : null;
	};

	return (
		<>
			<DuplicateStage {...stage} media={media} />
			{timed.length > 0 ? (
				<PlayerBar
					clock={clock}
					durations={durations}
					offset={offset}
					setOffset={setOffset}
				/>
			) : null}
		</>
	);
}
