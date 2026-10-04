import { EmptyState, Modal, Spinner, toast } from "@heroui/react";
import { Link } from "@tanstack/react-router";
import { Images, Pencil, Plus, Trash2 } from "lucide-react";
import { useRef, useState } from "react";
import { GridList, GridListItem } from "react-aria-components";
import { type Board, useBoards, useDeleteBoard } from "../boards";
import { useColumnWidth } from "../columnWidth";
import { AppHeader } from "../components/AppHeader";
import { AppNav } from "../components/AppNav";
import { BoardForm } from "../components/BoardForm";
import { IconButton } from "../components/IconButton";
import { RenditionImage } from "../components/RenditionImage";
import { renditionForColumn, tileStillSource } from "../lib/tiles";
import { useHandshake } from "../media";
import { m } from "../paraglide/messages.js";

/** What the dialog is open on: a board being renamed, or a board that does not exist yet. */
type Edited = Board | "new" | null;

/** The board's cover, square and still, or an empty square (specification 2026-10-05, decisions E and F). */
function Cover({ url }: { url: string | null }) {
	const ref = useRef<HTMLDivElement>(null);
	const width = useColumnWidth(ref);
	const smallRenditionPx = useHandshake().data?.renditionSizes?.small;
	const rendition = renditionForColumn(
		width,
		window.devicePixelRatio,
		smallRenditionPx,
	);

	return (
		<div ref={ref} className="w-full">
			{url === null ? (
				<span className="grid aspect-square place-content-center rounded bg-surface text-muted shadow-surface">
					<Images aria-hidden className="size-8" />
				</span>
			) : (
				// Decorative: the link it sits in is named by the board.
				<RenditionImage
					src={tileStillSource(url, rendition)}
					alt=""
					className="aspect-square w-full rounded object-cover"
				/>
			)}
		</div>
	);
}

function BoardTile({ board, rename }: { board: Board; rename: () => void }) {
	// A refusal has no form to speak in: the tile is where the gesture happened and it stays put.
	const remove = useDeleteBoard();

	return (
		<GridListItem
			id={board.id}
			textValue={board.name}
			className="flex min-w-0 flex-col gap-1 rounded outline-none data-focus-visible:ring-2 data-focus-visible:ring-focus"
		>
			{/* The cover and the name are the way in: a board's own grid is a screen, so it answers a
          middle click and opens in a new tab like any other address. */}
			<Link
				to="/boards/$boardId"
				params={{ boardId: board.id }}
				className="flex flex-col gap-2 font-medium hover:underline"
			>
				<Cover url={board.coverUrl} />
				<span className="truncate">{board.name}</span>
			</Link>
			<span className="truncate text-muted text-sm">{board.description}</span>
			{/* At the foot, so a tile with no description keeps its row's line. */}
			<div className="mt-auto flex items-center">
				<span className="flex-1 text-sm text-muted">
					{m.board_pin_count({ count: board.pinCount })}
				</span>
				<IconButton
					icon={Pencil}
					name={m.rename_board({ name: board.name })}
					variant="ghost"
					onPress={rename}
				/>
				<IconButton
					icon={Trash2}
					name={m.delete_board({ name: board.name })}
					variant="ghost"
					isDisabled={remove.isPending}
					onPress={() =>
						remove.mutate(board.id, {
							onError: () => toast.danger(m.board_deletion_refused()),
						})
					}
				/>
			</div>
		</GridListItem>
	);
}

/**
 * The boards as square tiles, each picked by its cover (specification 2026-10-05). The screen
 * carries no selection bar, a board being deleted from its own tile (specification 2026-09-20,
 * decision O).
 */
function BoardList({ rename }: { rename: (board: Board) => void }) {
	const boards = useBoards();

	if (boards.isPending) {
		return (
			<div
				role="status"
				className="grid place-content-center justify-items-center gap-2 text-muted"
			>
				{/* Hidden from the reader: the spinner carries a `status` role of its own. */}
				<Spinner aria-hidden />
				{m.boards_loading()}
			</div>
		);
	}
	if (boards.isError) {
		return <p role="alert">{m.boards_unreadable()}</p>;
	}
	if (boards.data.length === 0) {
		return (
			<EmptyState
				role="status"
				className="grid place-content-center text-center"
			>
				{m.boards_empty()}
			</EmptyState>
		);
	}

	return (
		<GridList
			aria-label={m.boards()}
			layout="grid"
			items={boards.data}
			className="grid grid-cols-[repeat(auto-fill,minmax(10rem,1fr))] gap-4 outline-none"
		>
			{(board) => <BoardTile board={board} rename={() => rename(board)} />}
		</GridList>
	);
}

export function Boards() {
	const [edited, setEdited] = useState<Edited>(null);

	return (
		<main className="flex w-full flex-col gap-4 p-4">
			<AppHeader>
				<IconButton
					icon={Plus}
					name={m.create_board()}
					className="order-last"
					onPress={() => setEdited("new")}
				/>
				<AppNav />
			</AppHeader>
			<BoardList rename={setEdited} />
			<Modal.Backdrop
				isOpen={edited !== null}
				onOpenChange={() => setEdited(null)}
				isDismissable
			>
				<Modal.Container size="sm">
					<Modal.Dialog>
						<Modal.CloseTrigger aria-label={m.close()} />
						<Modal.Heading level={2} className="mb-3 pe-8">
							{edited === "new" || edited === null
								? m.create_board()
								: m.rename_board({ name: edited.name })}
						</Modal.Heading>
						{edited === null ? null : (
							<BoardForm edited={edited} close={() => setEdited(null)} />
						)}
					</Modal.Dialog>
				</Modal.Container>
			</Modal.Backdrop>
		</main>
	);
}
