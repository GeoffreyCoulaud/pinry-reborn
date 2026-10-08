import { useParams, useSearch } from "@tanstack/react-router";
import { useId } from "react";
import { useBoards } from "../boards";
import { AppHeader } from "../components/AppHeader";
import { AppNav } from "../components/AppNav";
import { PinGrid } from "../components/PinGrid";
import { SearchField } from "../components/SearchField";
import { SortSelect } from "../components/SortSelect";
import { PIN_SORTS } from "../lib/sorts";
import { m } from "../paraglide/messages.js";

/**
 * One board's pins, in the grid the home screen renders. The drop that creates a pin is not here:
 * a board is not a place a drop files one (specification 2026-09-20, section 5).
 */
export function Board() {
	const { boardId } = useParams({ from: "/boards/$boardId" });
	const { sort, q } = useSearch({ from: "/boards/$boardId" });
	// The list arrives whole and is the query the boards screen already holds, so a board opened
	// from that screen costs no request of its own (2.7).
	const boards = useBoards();
	const board = boards.data?.find((one) => one.id === boardId);

	const heading = board?.name ?? m.boards();
	// An address is whatever the bar holds. A board the account does not hold says so where the
	// grid would be, so the screen keeps the navigation that leads back out of it.
	const unknown = boards.isSuccess && board === undefined;
	const collectionsLabel = useId();

	return (
		<main className="flex h-screen flex-col gap-4 px-4 pt-4">
			<AppHeader
				heading={heading}
				search={unknown ? null : <SearchField term={q} boardName={heading} />}
			>
				<AppNav />
				{unknown ? null : <SortSelect value={sort} values={PIN_SORTS} />}
			</AppHeader>
			{board?.description ? (
				<p className="text-muted">{board.description}</p>
			) : null}
			{board?.remoteCollections.length ? (
				<div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
					<span id={collectionsLabel} className="text-muted">
						{m.board_collections()}
					</span>
					<ul
						aria-labelledby={collectionsLabel}
						className="flex flex-wrap gap-x-3 gap-y-1"
					>
						{/* The server sorts them, and a sort here would collate otherwise. */}
						{board.remoteCollections.map((collection) => (
							<li key={collection.url}>
								<a
									href={collection.url}
									target="_blank"
									rel="noreferrer"
									className="text-accent hover:underline"
								>
									{collection.name}
								</a>
							</li>
						))}
					</ul>
				</div>
			) : null}
			{/* Full bleed: the scrollbar belongs to the viewport edge, not inside the shell's padding. */}
			<div className="-mx-4 min-h-0 flex-1">
				{unknown ? (
					<p role="alert" className="px-4">
						{m.board_unknown()}
					</p>
				) : (
					<PinGrid sort={sort} label={heading} boardId={boardId} term={q} />
				)}
			</div>
		</main>
	);
}
