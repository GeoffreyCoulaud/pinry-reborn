import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http } from "msw";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import {
	board,
	boardRoutes,
	downloadsRoute,
	handshakeRoute,
	onePinPage,
	readyPin,
	refused,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

const FIRST = readyPin("a harbour at dusk");
const SECOND = readyPin("a harbour at dawn");
const THIRD = readyPin("a cat asleep");

/** The catalogue and the boards, the created ones landing in the array the journey passes. */
function account(
	boards: Parameters<typeof boardRoutes>[0],
	...routes: Parameters<typeof server.use>
) {
	server.use(
		sessionRoute(() => true),
		onePinPage(() => [FIRST, SECOND, THIRD]),
		downloadsRoute(),
		handshakeRoute(),
		...routes,
		...boardRoutes(boards),
	);
}

/** Two tiles ticked, then the menu's first item, a name, and the dialog's own submit. */
async function fileUnderANewBoard(name: string) {
	const user = userEvent.setup();
	for (const pin of [FIRST, SECOND]) {
		await user.click(
			await screen.findByRole("checkbox", {
				name: new RegExp(pin.description),
			}),
		);
	}
	await user.click(screen.getByRole("button", { name: m.add_to_board() }));
	await user.click(
		await screen.findByRole("menuitem", { name: m.new_board() }),
	);
	await user.type(await screen.findByRole("textbox", { name: m.name() }), name);
	await user.click(
		within(screen.getByRole("dialog")).getByRole("button", {
			name: m.create_board(),
		}),
	);
	return user;
}

describe("add selected pins to a new board", () => {
	it("Given two pins selected, Then one request creates the board with both", async () => {
		const boards = [board("Harbours")];
		const sent: unknown[] = [];
		// Answers nothing, so the request falls through to the board routes' fake, which reads it again.
		account(
			boards,
			http.post(
				"/api/v1/boards",
				async ({ request }) => void sent.push(await request.clone().json()),
			),
		);
		const { router } = renderApp("/");

		const user = await fileUnderANewBoard("Boats");

		// The selection is spent and the grid is where the gesture left the user.
		expect(
			await screen.findByRole("img", { name: FIRST.description }),
		).toBeVisible();
		expect(screen.queryByRole("toolbar", { name: m.selection() })).toBeNull();
		expect(screen.queryByRole("dialog")).toBeNull();
		expect(router.state.location.pathname).toBe("/");
		expect(sent).toEqual([
			{ name: "Boats", description: "", pinIds: [FIRST.id, SECOND.id] },
		]);

		// The fake counts what it was sent, so the count shows the list was read again.
		await user.click(screen.getByRole("link", { name: m.boards() }));
		const row = await screen.findByRole("row", { name: /Boats/ });
		expect(
			within(row).getByText(m.board_pin_count({ count: 2 })),
		).toBeVisible();
	});

	it("Given a name the account already holds, Then the dialog says so and the selection stays", async () => {
		account(
			[],
			http.post("/api/v1/boards", () =>
				refused(409, "BOARD_NAME_ALREADY_EXISTS"),
			),
		);
		renderApp("/");

		await fileUnderANewBoard("Harbours");

		expect(
			await within(screen.getByRole("dialog")).findByRole("alert"),
		).toHaveTextContent(m.board_name_taken());
		expect(
			screen.getByText(m.selected_count({ count: 2 })),
		).toBeInTheDocument();
	});

	it("Given a recycled pin in the selection, Then the refusal is the general one and the selection stays", async () => {
		account(
			[],
			http.post("/api/v1/boards", () =>
				refused(409, "PIN_ALREADY_SOFT_DELETED"),
			),
		);
		renderApp("/");

		await fileUnderANewBoard("Boats");

		// The same status as a name taken, which would send the user to rename a board that is fine.
		expect(
			await within(screen.getByRole("dialog")).findByRole("alert"),
		).toHaveTextContent(m.board_refused());
		expect(
			screen.getByText(m.selected_count({ count: 2 })),
		).toBeInTheDocument();
	});
});
