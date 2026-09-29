import { Button, Dropdown, Modal, toast } from "@heroui/react";
import { useState } from "react";
import { Collection } from "react-aria-components";
import {
	useAddPinsToBoard,
	useBoards,
	useRemovePinsFromBoard,
} from "../boards";
import { m } from "../paraglide/messages.js";
import { useRecyclePins } from "../pins";
import { BoardForm } from "./BoardForm";

/** The menu's key for "New board…", which no board's identifier, a UUID, can take. */
const NEW_BOARD = "new";

/**
 * What the selection bar offers over tiles. Two gestures on the catalogue and three on a board's
 * grid: taking pins out of a board is only a gesture where there is a board to take them out of,
 * and every one of them is a single all-or-nothing request (ADR 0039).
 */
export function PinGestures({
	pinIds,
	boardId,
	clear,
}: {
	pinIds: readonly string[];
	boardId?: string;
	clear: () => void;
}) {
	const boards = useBoards();
	const add = useAddPinsToBoard();
	const remove = useRemovePinsFromBoard();
	const recycle = useRecyclePins();
	const [creating, setCreating] = useState(false);
	const spend = (message: string) => ({
		onSuccess: clear,
		onError: () => toast.danger(message),
	});

	return (
		<>
			{/* The board is chosen in the gesture rather than before it: the menu is the second half
          of one press, and there is nothing to undo if it is dismissed. */}
			<Dropdown>
				<Button variant="outline" isDisabled={add.isPending}>
					{m.add_to_board()}
				</Button>
				<Dropdown.Popover>
					<Dropdown.Menu
						aria-label={m.boards()}
						onAction={(key) =>
							key === NEW_BOARD
								? setCreating(true)
								: add.mutate(
										{ boardId: String(key), pinIds },
										spend(m.membership_refused()),
									)
						}
					>
						<Dropdown.Item id={NEW_BOARD}>{m.new_board()}</Dropdown.Item>
						<Collection items={boards.data ?? []}>
							{(held) => (
								<Dropdown.Item id={held.id}>{held.name}</Dropdown.Item>
							)}
						</Collection>
					</Dropdown.Menu>
				</Dropdown.Popover>
			</Dropdown>
			{/* A refusal keeps the dialog and the selection, so the gesture can be tried again. */}
			<Modal.Backdrop
				isOpen={creating}
				onOpenChange={setCreating}
				isDismissable
			>
				<Modal.Container size="sm">
					<Modal.Dialog>
						<Modal.CloseTrigger aria-label={m.close()} />
						<Modal.Heading level={2} className="mb-3 pe-8">
							{m.create_board()}
						</Modal.Heading>
						<BoardForm
							edited="new"
							pinIds={pinIds}
							close={() => {
								setCreating(false);
								clear();
							}}
						/>
					</Modal.Dialog>
				</Modal.Container>
			</Modal.Backdrop>
			{boardId !== undefined && (
				<Button
					variant="outline"
					isDisabled={remove.isPending}
					onPress={() =>
						remove.mutate({ boardId, pinIds }, spend(m.membership_refused()))
					}
				>
					{m.remove_from_board()}
				</Button>
			)}
			<Button
				variant="danger-soft"
				isDisabled={recycle.isPending}
				onPress={() => recycle.mutate(pinIds, spend(m.pin_deletion_refused()))}
			>
				{m.delete_pin()}
			</Button>
		</>
	);
}
