import { Button, Input, Label, TextField } from "@heroui/react";
import {
	type Board,
	BoardRefusal,
	useCreateBoard,
	useSaveBoard,
} from "../boards";
import { m } from "../paraglide/messages.js";

function Field({
	name,
	label,
	defaultValue,
	isRequired,
}: {
	name: string;
	label: string;
	defaultValue: string;
	isRequired?: boolean;
}) {
	// `secondary` is HeroUI's field on a surface: the default one takes the dialog's own colour in dark.
	return (
		<TextField
			name={name}
			defaultValue={defaultValue}
			isRequired={isRequired}
			variant="secondary"
		>
			<Label>{label}</Label>
			<Input />
		</TextField>
	);
}

/**
 * One form for both writes: the route that renames a board replaces it, description included. A
 * board created from a selection is created with its pins, in the same request.
 */
export function BoardForm({
	edited,
	close,
	pinIds = [],
}: {
	edited: Board | "new";
	close: () => void;
	pinIds?: readonly string[];
}) {
	const create = useCreateBoard();
	const save = useSaveBoard();
	const board = edited === "new" ? null : edited;
	const refusal = create.error ?? save.error;

	return (
		<form
			className="flex flex-col gap-3"
			onSubmit={(event) => {
				event.preventDefault();
				const fields = new FormData(event.currentTarget);
				const body = {
					name: String(fields.get("name")),
					description: String(fields.get("description")),
				};
				if (board === null) {
					create.mutate({ ...body, pinIds: [...pinIds] }, { onSuccess: close });
				} else {
					save.mutate({ boardId: board.id, body }, { onSuccess: close });
				}
			}}
		>
			<Field
				name="name"
				label={m.name()}
				defaultValue={board?.name ?? ""}
				isRequired
			/>
			<Field
				name="description"
				label={m.description()}
				defaultValue={board?.description ?? ""}
			/>
			{refusal === null ? null : (
				<p role="alert">
					{refusal instanceof BoardRefusal && refusal.nameTaken
						? m.board_name_taken()
						: m.board_refused()}
				</p>
			)}
			<Button
				type="submit"
				className="self-end"
				isDisabled={create.isPending || save.isPending}
			>
				{board === null ? m.create_board() : m.save()}
			</Button>
		</form>
	);
}
