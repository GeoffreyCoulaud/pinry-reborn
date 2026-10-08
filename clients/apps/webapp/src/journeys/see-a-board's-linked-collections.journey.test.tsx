import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
	board,
	boardPinsRoute,
	boardRoutes,
	downloadsRoute,
	handshakeRoute,
	onePinPage,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

const WRECKS = { name: "Wrecks", url: "https://remote.example.test/wrecks" };
const LIGHTHOUSES = {
	name: "Lighthouses",
	url: "https://remote.example.test/lighthouses",
};
const HARBOURS = {
	...board("Harbours", "Where the boats are"),
	remoteCollections: [WRECKS, LIGHTHOUSES],
};

describe("see a board's linked collections", () => {
	it("Given a board linked to two collections, Then its page lists them in the server's order, each linking to its address", async () => {
		server.use(
			sessionRoute(() => true),
			onePinPage(() => []),
			boardPinsRoute({ [HARBOURS.id]: [[]] }),
			downloadsRoute(),
			handshakeRoute(),
			...boardRoutes([HARBOURS]),
		);

		renderApp(`/boards/${HARBOURS.id}`);

		const list = await screen.findByRole("list", {
			name: "Linked collections",
		});
		const links = within(list).getAllByRole("link");
		expect(links.map((link) => link.textContent)).toEqual([
			WRECKS.name,
			LIGHTHOUSES.name,
		]);
		expect(links[0]).toHaveAttribute("href", WRECKS.url);
		expect(links[0]).toHaveAttribute("target", "_blank");
		expect(links[1]).toHaveAttribute("href", LIGHTHOUSES.url);
	});
});
