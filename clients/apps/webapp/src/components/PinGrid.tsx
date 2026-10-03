import { EmptyState, Modal, Spinner } from "@heroui/react";
import { Play } from "lucide-react";
import {
	type RefObject,
	useEffect,
	useLayoutEffect,
	useRef,
	useState,
} from "react";
import {
	Collection,
	GridList,
	GridListItem,
	GridListLoadMoreItem,
	Size,
	Virtualizer,
	WaterfallLayout,
} from "react-aria-components";
import { preload } from "react-dom";
import { downloadReason } from "../downloadReasons";
import { isVideo } from "../lib/media";
import type { PinSort } from "../lib/sorts";
import {
	neighbours,
	placeableTiles,
	type Rendition,
	renditionForColumn,
	tileAspectRatio,
	tileMediaSource,
} from "../lib/tiles";
import { useHandshake } from "../media";
import { m } from "../paraglide/messages.js";
import { type Pin, usePins } from "../pins";
import { useSelection } from "../selection";
import { PinDialog } from "./PinDialog";
import { PinGestures } from "./PinGestures";
import { SelectionBar, SelectionTick } from "./SelectionBar";

/**
 * Every bound here is finite, and two of them have to be. `WaterfallLayout` reads the scroll
 * view's width, which react-aria reports as infinite under test, so an unbounded `maxColumns`,
 * `maxItemSize` or `maxHorizontalSpace` lays the grid out at `NaN`. A finite column is also what
 * a tile wants: past the medium rendition the server has no more pixels to give it.
 */
const LAYOUT = {
	minItemSize: new Size(200, 200),
	maxItemSize: new Size(480, 960),
	maxHorizontalSpace: 16,
	maxColumns: 8,
};

/** The column's width, which the layout gives the item and only a browser can measure. */
function useColumnWidth(ref: RefObject<HTMLElement | null>): number {
	const [width, setWidth] = useState(0);
	useLayoutEffect(() => {
		const measured = ref.current?.clientWidth ?? 0;
		if (measured !== width) {
			setWidth(measured);
		}
	});
	return width;
}

/**
 * The tile carries its ratio so the layout measures it at its true height on the first pass and
 * its column settles once, before a byte of the image arrives (specification 2026-09-10, 4.7).
 */
function Tile({
	pin,
	smallRenditionPx,
	onRendition,
}: {
	pin: Pin;
	smallRenditionPx?: number;
	onRendition: (rendition: Rendition) => void;
}) {
	const ref = useRef<HTMLDivElement>(null);
	const columnWidth = useColumnWidth(ref);
	const media = pin.media;
	const ratio = { aspectRatio: tileAspectRatio(media?.width, media?.height) };
	const rendition = renditionForColumn(
		columnWidth,
		window.devicePixelRatio,
		smallRenditionPx,
	);
	const [hovered, setHovered] = useState(false);
	const video = isVideo(media?.mimeType);
	// Every column is as wide, so the viewer loads under the rendition any tile chose.
	useEffect(() => onRendition(rendition), [onRendition, rendition]);

	return (
		<div
			ref={ref}
			className="relative w-full"
			// A touch would fetch the original for the instant before its tap opens the pin.
			onPointerEnter={(event) => setHovered(event.pointerType !== "touch")}
			onPointerLeave={() => setHovered(false)}
		>
			{media?.url ? (
				<>
					<img
						src={tileMediaSource(media.url, rendition)}
						alt={pin.description}
						style={ratio}
						className="w-full rounded object-cover"
					/>
					{video && hovered ? (
						<video
							src={media.url}
							poster={tileMediaSource(media.url, rendition)}
							muted
							loop
							autoPlay
							playsInline
							aria-hidden
							className="absolute inset-0 size-full rounded object-cover"
						/>
					) : null}
					{video ? (
						<Play
							role="img"
							aria-label={m.video_badge()}
							className="absolute end-2 top-2 size-6 rounded-full bg-black/60 p-1 text-white"
						/>
					) : null}
				</>
			) : (
				// A failed download keeps its tile and says why; a pin with no image at all says what it is.
				<p
					style={ratio}
					className="grid place-content-center rounded bg-surface p-2 text-center shadow-surface"
				>
					{downloadReason(media?.reasonCode, media?.message) ?? pin.description}
				</p>
			)}
		</div>
	);
}

/** The neighbours' placeholder and never their original, so a step shows an image at once. */
function preloadNeighbours(around: (Pin | undefined)[], rendition: Rendition) {
	for (const neighbour of around) {
		if (neighbour?.media?.url) {
			preload(tileMediaSource(neighbour.media.url, rendition), { as: "image" });
		}
	}
}

/**
 * The catalogue as tiles, or one board's share of it. The home screen and a board's screen render
 * the same grid; what surrounds it, the drop that creates a pin included, is the screen's own.
 */
export function PinGrid({
	sort,
	label,
	boardId,
	term,
}: {
	sort: PinSort;
	label: string;
	boardId?: string;
	term?: string;
}) {
	const pins = usePins(sort, boardId, term);
	// The breakpoint a tile picks its rendition on is the deployment's, not a constant: `small`
	// lowered in the configuration would otherwise upscale every tile (specification 4.3).
	const renditionSizes = useHandshake().data?.renditionSizes;
	const [openedId, setOpenedId] = useState<string | null>(null);
	const [rendition, setRendition] = useState<Rendition>("SMALL");
	const loaded = pins.data?.pages.flatMap((page) => page.pins) ?? [];
	const tiles = placeableTiles(loaded);
	// Among every loaded pin, so a retried download that turns it `PENDING` keeps it open (decision E).
	const opened = loaded.find((pin) => pin.id === openedId);
	const { previous, next } = neighbours(loaded, openedId);
	const selection = useSelection(tiles);
	const selecting = selection.ids.length > 0;

	// Past the last loaded pin the viewer asks for the page itself, the grid's sentinel not being in view.
	const fetchThenStep = async () => {
		const from = openedId;
		const { data } = await pins.fetchNextPage();
		const arrived = neighbours(
			data?.pages.flatMap((page) => page.pins) ?? [],
			from,
		).next;
		// Only from the pin it left, so a viewer closed meanwhile stays closed.
		if (arrived) {
			setOpenedId((current) => (current === from ? arrived.id : current));
		}
	};
	preloadNeighbours(opened ? [previous, next] : [], rendition);
	const stepToPrevious = previous ? () => setOpenedId(previous.id) : undefined;
	const canFetch = pins.hasNextPage && !pins.isFetchingNextPage;
	const fetchStep = canFetch ? () => void fetchThenStep() : undefined;
	const stepToNext = next ? () => setOpenedId(next.id) : fetchStep;

	// Neither a first load nor an account with nothing in it draws a tile, and both said so with
	// a blank rectangle until now.
	if (pins.isPending) {
		return (
			<div
				role="status"
				className="grid h-full place-content-center justify-items-center gap-2 text-muted"
			>
				{/* Hidden from the reader: the spinner carries a `status` role of its own, and the
            sentence beside it is the one this region should announce. */}
				<Spinner aria-hidden />
				{m.pins_loading()}
			</div>
		);
	}
	// A refusal is not an empty account, and the empty state below would state one.
	if (pins.isError) {
		return <p role="alert">{m.pins_unreadable()}</p>;
	}
	// A search that matched nothing is not an empty account, and the recourse differs: add a pin,
	// or search for something else (specification 2026-09-21, decision N).
	// An open pin gone `PENDING` may leave no tile, and the empty state would take its dialog with it.
	if (tiles.length === 0 && opened === undefined) {
		return (
			<EmptyState
				role="status"
				className="grid h-full place-content-center text-center"
			>
				{term === undefined ? m.pins_empty() : m.search_empty({ term })}
			</EmptyState>
		);
	}

	return (
		<div className="flex h-full flex-col gap-2">
			<SelectionBar count={selection.ids.length} clear={selection.clear}>
				<PinGestures
					pinIds={selection.ids}
					boardId={boardId}
					clear={selection.clear}
				/>
			</SelectionBar>
			<Virtualizer layout={WaterfallLayout} layoutOptions={LAYOUT}>
				<GridList
					aria-label={label}
					layout="grid"
					{...selection.props}
					// react-aria writes no `overflow` on what it virtualizes: without this the window scrolls.
					className="min-h-0 flex-1 overflow-x-hidden overflow-y-auto outline-none"
					onAction={(key) => setOpenedId(String(key))}
				>
					{/* A collection renders its items once and keeps them: without `dependencies` the ticks
              would not hear that the grid now holds a selection. */}
					<Collection items={tiles} dependencies={[selecting]}>
						{/* `group` is what the tick's reveal on hover and on focus hangs off. It replaces
                react-aria's own class name, which nothing in this application styles. A selected
                tile is ringed and tinted so the selection reads at a glance. */}
						{(pin) => (
							<GridListItem
								textValue={pin.description}
								className="group rounded data-selected:ring-4 data-selected:ring-accent data-selected:after:pointer-events-none data-selected:after:absolute data-selected:after:inset-0 data-selected:after:rounded data-selected:after:bg-accent/25"
							>
								<SelectionTick
									shown={selecting}
									className="absolute start-2 top-2 z-10"
								/>
								<Tile
									pin={pin}
									smallRenditionPx={renditionSizes?.small}
									onRendition={setRendition}
								/>
							</GridListItem>
						)}
					</Collection>
					{/*
            The sentinel is re-observed on every collection change, and its own loading flag is
            one such change, so an unguarded `onLoadMore` re-enters until the test times out.
          */}
					<GridListLoadMoreItem
						onLoadMore={() => {
							if (pins.hasNextPage && !pins.isFetchingNextPage) {
								void pins.fetchNextPage();
							}
						}}
						isLoading={pins.isFetchingNextPage}
					/>
				</GridList>
			</Virtualizer>
			{/* The backdrop is the root here: a tile opens this modal, and the `Modal` root is a
          `DialogTrigger` that warns when it has no pressable child. */}
			<Modal.Backdrop
				isOpen={opened !== undefined}
				onOpenChange={() => setOpenedId(null)}
				isDismissable
			>
				{/* The window less HeroUI's margin, and the whole screen below `sm`, where `cover` keeps
            both the margin and the corners (specification 2026-09-27, decision A). */}
				<Modal.Container size="cover" scroll="inside" className="max-sm:p-0">
					<Modal.Dialog
						aria-label={opened?.description}
						className="max-sm:rounded-none"
					>
						{opened ? (
							<PinDialog
								pin={opened}
								close={() => setOpenedId(null)}
								placeholder={rendition}
								previous={stepToPrevious}
								next={stepToNext}
							/>
						) : null}
					</Modal.Dialog>
				</Modal.Container>
			</Modal.Backdrop>
		</div>
	);
}
