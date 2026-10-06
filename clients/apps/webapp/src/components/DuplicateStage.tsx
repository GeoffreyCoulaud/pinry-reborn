import { Chip } from "@heroui/react";
import { ChevronsLeftRight, Crown, Minus, Plus } from "lucide-react";
import {
	type PointerEvent,
	type MouseEvent as ReactMouseEvent,
	useEffect,
	useRef,
} from "react";
import { isVideo } from "../lib/media";
import { tileStillSource } from "../lib/tiles";
import { MAX_ZOOM, type View, zoomAt } from "../lib/zoom";
import { m } from "../paraglide/messages.js";
import { getLocale } from "../paraglide/runtime.js";
import type { Pin } from "../pins";
import { IconButton } from "./IconButton";

/** How far one press of `−` or `+`, or one wheel notch, zooms. */
const STEP = 1.5;
const WHEEL_STEP = 1.2;
/** Where a double-click zooms in to. */
const CLOSE_UP = 2.5;
/** How far an arrow key moves the line, in percent of the stage. */
const SPLIT_STEP = 5;

/** A still image's original; a video's still rendition until block 50 plays it. */
function stageSource(url: string, mimeType?: string | null) {
	return isVideo(mimeType) ? tileStillSource(url, "LARGE") : url;
}

/** One version drawn under the shared zoom and pan. */
function Layer({ version, view }: { version: Pin; view: View }) {
	const url = version.media?.url;
	return (
		<div
			className="absolute inset-4"
			style={{
				transform: `translate(${view.x}px, ${view.y}px) scale(${view.zoom})`,
			}}
		>
			{url ? (
				<img
					src={stageSource(url, version.media?.mimeType)}
					alt=""
					draggable={false}
					className="pointer-events-none size-full object-contain"
				/>
			) : null}
		</div>
	);
}

/** The pointer's position from the stage's centre. */
function fromCentre(
	stage: HTMLElement,
	event: { clientX: number; clientY: number },
) {
	const box = stage.getBoundingClientRect();
	return {
		x: event.clientX - (box.left + box.width / 2),
		y: event.clientY - (box.top + box.height / 2),
	};
}

/** The version under review left of a line and the kept one right of it, under one zoom (decision A). */
export function DuplicateStage({
	under,
	kept,
	view,
	setView,
	split,
	setSplit,
}: {
	under: Pin;
	kept: Pin;
	view: View;
	setView: (change: (view: View) => View) => void;
	split: number;
	setSplit: (split: number) => void;
}) {
	const stage = useRef<HTMLElement>(null);
	const drag = useRef<{
		grip: boolean;
		x: number;
		y: number;
		from: View;
	} | null>(null);
	const paired = under.id !== kept.id;

	// React listens to the wheel passively, and the dialog would scroll under the zoom.
	useEffect(() => {
		const element = stage.current;
		if (element === null) {
			return;
		}
		const wheel = (event: WheelEvent) => {
			event.preventDefault();
			const factor = event.deltaY < 0 ? WHEEL_STEP : 1 / WHEEL_STEP;
			setView((current) => zoomAt(current, factor, fromCentre(element, event)));
		};
		element.addEventListener("wheel", wheel, { passive: false });
		return () => element.removeEventListener("wheel", wheel);
	}, [setView]);

	const isInside = (target: EventTarget, selector: string) =>
		target instanceof Element && target.closest(selector) !== null;
	const isControl = (target: EventTarget) =>
		isInside(target, "[data-stage-control]");
	const down = (event: PointerEvent<HTMLElement>) => {
		const grip = isInside(event.target, "[data-grip]");
		// The grip drags the line; elsewhere a drag pans, once zoomed.
		if (!grip && (isControl(event.target) || view.zoom <= 1)) {
			return;
		}
		drag.current = { grip, x: event.clientX, y: event.clientY, from: view };
		event.currentTarget.setPointerCapture(event.pointerId);
	};
	const move = (event: PointerEvent<HTMLElement>) => {
		const held = drag.current;
		if (held?.grip) {
			const box = event.currentTarget.getBoundingClientRect();
			setSplit(
				Math.min(
					100,
					Math.max(0, ((event.clientX - box.left) / box.width) * 100),
				),
			);
		} else if (held) {
			setView(() => ({
				...held.from,
				x: held.from.x + event.clientX - held.x,
				y: held.from.y + event.clientY - held.y,
			}));
		}
	};
	const doubleClick = (event: ReactMouseEvent<HTMLElement>) => {
		if (!isControl(event.target)) {
			const point = fromCentre(event.currentTarget, event);
			setView((current) =>
				zoomAt(current, current.zoom > 1 ? 1 / current.zoom : CLOSE_UP, point),
			);
		}
	};

	return (
		// biome-ignore lint/a11y/noNoninteractiveElementInteractions: the drags and the wheel are the pointer's; the keyboard has the buttons and the grip.
		<figure
			ref={stage}
			aria-label={
				paired
					? `${under.description} · ${kept.description}`
					: under.description
			}
			className={`relative h-80 shrink-0 touch-none select-none overflow-hidden bg-background-secondary lg:h-auto lg:min-h-0 lg:flex-1 ${view.zoom > 1 ? "cursor-grab active:cursor-grabbing" : ""}`}
			onPointerDown={down}
			onPointerMove={move}
			onPointerUp={() => {
				drag.current = null;
			}}
			onPointerCancel={() => {
				drag.current = null;
			}}
			onDoubleClick={doubleClick}
		>
			{paired ? (
				<>
					<Layer version={kept} view={view} />
					<div
						className="absolute inset-0"
						style={{ clipPath: `inset(0 ${100 - split}% 0 0)` }}
					>
						<Layer version={under} view={view} />
					</div>
					<div
						className="absolute inset-y-0 w-0.5 -translate-x-px bg-white shadow-[0_0_4px_rgb(0_0_0/0.5)]"
						style={{ left: `${split}%` }}
					>
						<button
							type="button"
							data-stage-control
							data-grip
							aria-label={m.compare_split()}
							className="absolute top-1/2 left-1/2 grid size-9 -translate-x-1/2 -translate-y-1/2 cursor-ew-resize place-items-center rounded-full bg-white text-black shadow-md outline-none focus-visible:ring-2 focus-visible:ring-focus"
							onKeyDown={(event) => {
								if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
									// Kept from the version switch, which listens on the document.
									event.preventDefault();
									const step =
										event.key === "ArrowLeft" ? -SPLIT_STEP : SPLIT_STEP;
									setSplit(Math.min(100, Math.max(0, split + step)));
								}
							}}
						>
							<ChevronsLeftRight aria-hidden className="size-4" />
						</button>
					</div>
					<Chip
						size="sm"
						className="pointer-events-none absolute start-3 top-3"
					>
						{m.compare_this_version()}
					</Chip>
					<Chip
						size="sm"
						color="warning"
						className="pointer-events-none absolute end-3 top-3"
					>
						<Crown aria-hidden className="size-3" />
						{m.compare_kept()}
					</Chip>
				</>
			) : (
				<Layer version={under} view={view} />
			)}
			<div
				data-stage-control
				className="absolute end-3 bottom-3 flex items-center rounded-full bg-surface p-0.5 shadow-surface"
			>
				<IconButton
					icon={Minus}
					name={m.compare_zoom_out()}
					size="sm"
					variant="ghost"
					isDisabled={view.zoom <= 1}
					onPress={() => setView((current) => zoomAt(current, 1 / STEP))}
				/>
				<span className="min-w-12 text-center text-xs tabular-nums">
					{new Intl.NumberFormat(getLocale(), { style: "percent" }).format(
						view.zoom,
					)}
				</span>
				<IconButton
					icon={Plus}
					name={m.compare_zoom_in()}
					size="sm"
					variant="ghost"
					isDisabled={view.zoom >= MAX_ZOOM}
					onPress={() => setView((current) => zoomAt(current, STEP))}
				/>
			</div>
		</figure>
	);
}
