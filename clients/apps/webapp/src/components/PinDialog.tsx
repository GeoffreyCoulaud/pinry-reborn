import { Button, Chip, toast } from "@heroui/react";
import { Link } from "@tanstack/react-router";
import { ChevronLeft, ChevronRight, Pencil, Trash2, X } from "lucide-react";
import { type PointerEvent, useEffect, useRef, useState } from "react";
import { useMove } from "react-aria";
import type { Rendition } from "../lib/tiles";
import { m } from "../paraglide/messages.js";
import { type Pin, useRecyclePins } from "../pins";
import { IconButton } from "./IconButton";
import { PinDuplicates } from "./PinDuplicates";
import { PinEditForm } from "./PinEditForm";
import { PinMedia } from "./PinMedia";
import { PinSides } from "./PinSides";

/** The column beside the image. */
function PinDetails({
	pin,
	close,
	edit,
	openDuplicate,
}: {
	pin: Pin;
	close: () => void;
	edit: () => void;
	openDuplicate: (pinId: string) => void;
}) {
	const recycle = useRecyclePins();
	const source = pin.sourceContextUrl;

	return (
		<>
			<div className="flex items-center gap-1">
				<Button variant="ghost" onPress={edit}>
					<Pencil aria-hidden />
					{m.edit_pin()}
				</Button>
				{/* Kept away from Close: no confirmation guards it, the bin being how the pin comes back. */}
				<IconButton
					icon={Trash2}
					name={m.delete_pin()}
					variant="ghost"
					isDisabled={recycle.isPending}
					onPress={() =>
						recycle.mutate([pin.id], {
							onSuccess: close,
							onError: () => toast.danger(m.pin_deletion_refused()),
						})
					}
				/>
				<IconButton
					icon={X}
					name={m.close()}
					variant="ghost"
					className="ms-auto"
					onPress={close}
				/>
			</div>
			<p>{pin.description}</p>
			<dl className="flex flex-col gap-4 [&_dt]:mb-1 [&_dt]:text-sm [&_dt]:text-muted">
				{source ? (
					<div>
						<dt>{m.source_page()}</dt>
						<dd>
							<a
								href={source}
								target="_blank"
								rel="noreferrer"
								className="text-accent hover:underline"
							>
								{URL.parse(source)?.hostname ?? source}
							</a>
						</dd>
					</div>
				) : null}
				{pin.tags.length > 0 ? (
					<div>
						<dt>{m.tags()}</dt>
						<dd className="flex flex-wrap gap-2">
							{pin.tags.map((tag) => (
								<Chip key={tag.name}>{tag.name}</Chip>
							))}
						</dd>
					</div>
				) : null}
				{pin.boards.length > 0 ? (
					<div>
						<dt>{m.boards()}</dt>
						<dd className="flex flex-col items-start gap-1">
							{pin.boards.map((board) => (
								<Link
									key={board.id}
									to="/boards/$boardId"
									params={{ boardId: board.id }}
									className="text-accent hover:underline"
								>
									{board.name}
								</Link>
							))}
						</dd>
					</div>
				) : null}
			</dl>
			<PinDuplicates pinId={pin.id} open={openDuplicate} />
		</>
	);
}

/** How far a touch travels sideways to step. No measurement set it: it is the knob to tune on a phone. */
const SWIPE_PX = 48;

/** A horizontal touch swipe steps, summed over the move since a cancelled pan ends it too (decision F). */
function useSwipe(previous?: () => void, next?: () => void) {
	const travel = useRef({ x: 0, y: 0 });
	const { moveProps } = useMove({
		onMoveStart: () => {
			travel.current = { x: 0, y: 0 };
		},
		onMove: ({ deltaX, deltaY }) => {
			travel.current.x += deltaX;
			travel.current.y += deltaY;
		},
		onMoveEnd: () => {
			const { x, y } = travel.current;
			if (Math.abs(x) >= SWIPE_PX && Math.abs(x) > Math.abs(y)) {
				(x < 0 ? next : previous)?.();
			}
		},
	});
	// Its pointer down alone, for touch alone: it prevents a mouse from dragging the image out, and
	// its key handler would swallow the arrows the dialog steps on. A video's controls take their own drags.
	return (event: PointerEvent<HTMLElement>) => {
		if (
			event.pointerType === "touch" &&
			!(event.target instanceof HTMLMediaElement)
		) {
			moveProps.onPointerDown?.(event);
		}
	};
}

/**
 * `←` and `→` on the document: the dialog holds the focus once open, and passes on no key handler.
 * A video's own arrows seek it.
 */
function useArrowKeys(previous?: () => void, next?: () => void) {
	useEffect(() => {
		const step = (event: KeyboardEvent) => {
			if (
				event.altKey ||
				event.ctrlKey ||
				event.metaKey ||
				event.shiftKey ||
				event.defaultPrevented ||
				event.target instanceof HTMLMediaElement
			) {
				return;
			}
			if (event.key === "ArrowLeft") {
				previous?.();
			}
			if (event.key === "ArrowRight") {
				next?.();
			}
		};
		document.addEventListener("keydown", step);
		return () => document.removeEventListener("keydown", step);
	}, [previous, next]);
}

/** The pin read or edited beside its image; nothing steps while the form is open. */
export function PinDialog({
	pin,
	close,
	placeholder,
	previous,
	next,
	openDuplicate,
}: {
	pin: Pin;
	close: () => void;
	placeholder: Rendition;
	previous?: () => void;
	next?: () => void;
	openDuplicate: (pinId: string) => void;
}) {
	const [editing, setEditing] = useState(false);
	const swipe = useSwipe(previous, next);
	useArrowKeys(editing ? undefined : previous, editing ? undefined : next);

	if (editing) {
		return (
			<PinEditForm
				pin={pin}
				media={<PinMedia pin={pin} placeholder={placeholder} retries={false} />}
				close={() => setEditing(false)}
			/>
		);
	}

	return (
		<PinSides
			onPointerDown={swipe}
			media={
				<>
					{/* Keyed, like the column: stepping would otherwise carry one pin's mutation onto the next. */}
					<PinMedia key={pin.id} pin={pin} placeholder={placeholder} />
					<IconButton
						icon={ChevronLeft}
						name={m.pin_previous()}
						variant="secondary"
						isDisabled={previous === undefined}
						onPress={previous}
						className="absolute start-2 top-1/2 -translate-y-1/2"
					/>
					<IconButton
						icon={ChevronRight}
						name={m.pin_next()}
						variant="secondary"
						isDisabled={next === undefined}
						onPress={next}
						className="absolute end-2 top-1/2 -translate-y-1/2"
					/>
				</>
			}
			column={
				<PinDetails
					key={pin.id}
					pin={pin}
					close={close}
					edit={() => setEditing(true)}
					openDuplicate={openDuplicate}
				/>
			}
		/>
	);
}
