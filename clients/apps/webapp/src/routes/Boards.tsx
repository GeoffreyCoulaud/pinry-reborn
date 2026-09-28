import { EmptyState, Modal, Spinner, toast } from "@heroui/react";
import { Link } from "@tanstack/react-router";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { GridList, GridListItem } from "react-aria-components";
import { type Board, useBoards, useDeleteBoard } from "../boards";
import { AppHeader } from "../components/AppHeader";
import { AppNav } from "../components/AppNav";
import { BoardForm } from "../components/BoardForm";
import { IconButton } from "../components/IconButton";
import { m } from "../paraglide/messages.js";

/** What the dialog is open on: a board being renamed, or a board that does not exist yet. */
type Edited = Board | "new" | null;

function BoardRow({ board, rename }: { board: Board; rename: () => void }) {
	// A refusal has no form to speak in: the row is where the gesture happened and it stays put.
	const remove = useDeleteBoard();

	return (
		<GridListItem
			id={board.id}
			textValue={board.name}
			className="flex flex-wrap items-center gap-3 border-b border-separator px-2 py-3 outline-none last:border-0"
		>
			{/* The description under the name rather than beside it: at a phone's width a row has no
          space for both, and truncating it to two words says nothing at all. */}
			<div className="flex min-w-0 flex-1 flex-col">
				{/* The name is the way in: a board's own grid is a screen, so it answers a middle click
            and opens in a new tab like any other address. */}
				<Link
					to="/boards/$boardId"
					params={{ boardId: board.id }}
					className="self-start font-medium hover:underline"
				>
					{board.name}
				</Link>
				<span className="truncate text-muted">{board.description}</span>
			</div>
			<span className="text-sm text-muted">
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
		</GridListItem>
	);
}

/**
 * The boards as a list and not a grid of tiles: the contract serves no cover, so there is nothing
 * to show but the words (specification 2026-09-20, decision N). `GridList` is the accessibility
 * role, and the screen carries
 * no selection bar, a board being deleted from its own row (decision O).
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
			items={boards.data}
			className="outline-none"
		>
			{(board) => <BoardRow board={board} rename={() => rename(board)} />}
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
						{edited !== null && (
							<BoardForm edited={edited} close={() => setEdited(null)} />
						)}
					</Modal.Dialog>
				</Modal.Container>
			</Modal.Backdrop>
		</main>
	);
}
