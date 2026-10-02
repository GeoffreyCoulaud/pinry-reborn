import type { PointerEventHandler, ReactNode } from "react";

/**
 * The image beside its column from `lg`, stacked below it (specification 2026-09-27, decision A).
 * Reading and editing share it, so an edit leaves the image where it is (decision G).
 */
export function PinSides({
	media,
	column,
	onPointerDown,
}: {
	media: ReactNode;
	column: ReactNode;
	onPointerDown?: PointerEventHandler<HTMLElement>;
}) {
	return (
		<div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto lg:flex-row lg:overflow-hidden">
			{/* The height the image fits in: its own side from `lg`, a share of the screen once stacked.
          `pan-y` leaves the vertical scroll to the browser, which then cancels the swipe. */}
			<div
				className="relative flex touch-pan-y items-center justify-center [--fit-height:70dvh] lg:min-w-0 lg:flex-1 lg:[--fit-height:100cqh] lg:[container-type:size]"
				onPointerDown={onPointerDown}
			>
				{media}
			</div>
			{/* Fixed, so that what a wider window adds goes to the image. */}
			<div className="flex flex-col gap-4 lg:w-[22.5rem] lg:shrink-0 lg:overflow-y-auto">
				{column}
			</div>
		</div>
	);
}
