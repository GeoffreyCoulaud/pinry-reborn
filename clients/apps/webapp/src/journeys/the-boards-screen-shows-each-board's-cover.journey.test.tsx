import { fireEvent, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import {
	board,
	boardRoutes,
	downloadsRoute,
	handshakeRoute,
	onePinPage,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

const COVERED = {
	...board("Harbours"),
	coverUrl: "/api/v1/pins/0f5c6e58-2d6c-4a3a-9c1f-0000000000c1/media",
};
const BARE = board("Mountains");

function account() {
	server.use(
		sessionRoute(() => true),
		onePinPage(() => []),
		downloadsRoute(),
		handshakeRoute(),
		...boardRoutes([COVERED, BARE]),
	);
}

describe("the boards screen shows each board's cover", () => {
	it("Given a board with a cover and one without, Then the first shows its still and the second a square placeholder", async () => {
		account();

		renderApp("/boards");

		const covered = await screen.findByRole("row", { name: "Harbours" });
		expect(covered.querySelector("img")).toHaveAttribute(
			"src",
			`${COVERED.coverUrl}?size=SMALL&animated=false`,
		);
		expect(covered.querySelector("img")).toHaveClass("aspect-square");
		const bare = screen.getByRole("row", { name: "Mountains" });
		expect(bare.querySelector("img")).toBeNull();
		expect(bare.querySelector(".aspect-square")).not.toBeNull();
	});

	it("Given a board with a cover, Then its image waits for the screen to reach it", async () => {
		account();

		renderApp("/boards");

		const covered = await screen.findByRole("row", { name: "Harbours" });
		expect(covered.querySelector("img")).toHaveAttribute("loading", "lazy");
	});

	it("Given a cover the server cannot draw, Then the board's link is still named by the board alone", async () => {
		account();
		renderApp("/boards");
		const covered = await screen.findByRole("row", { name: "Harbours" });
		const cover = covered.querySelector("img");
		if (cover === null) {
			throw new Error("No cover was drawn.");
		}

		fireEvent.error(cover);

		expect(within(covered).getByTitle(m.preview_unavailable())).toBeVisible();
		expect(
			within(covered).getByRole("link", { name: "Harbours" }),
		).toBeVisible();
	});

	it("Given the tiles laid out as a grid, Then the right arrow moves to the next board", async () => {
		account();

		renderApp("/boards");
		await screen.findByRole("row", { name: "Harbours" });
		await userEvent.click(screen.getByRole("row", { name: "Harbours" }));
		await userEvent.keyboard("{ArrowRight}");

		// Laid out as a list, the arrow would enter the row and land on its link instead.
		expect(screen.getByRole("row", { name: "Mountains" })).toHaveFocus();
	});
});
