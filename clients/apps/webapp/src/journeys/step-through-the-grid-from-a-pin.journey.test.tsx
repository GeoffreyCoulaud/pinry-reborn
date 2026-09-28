import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { m } from "../paraglide/messages.js";
import type { Pin } from "../pins";
import {
	downloadsRoute,
	handshakeRoute,
	pinsRoute,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** The grid on one page of pins, with the first of them open. */
async function openTheFirst(pins: Pin[]) {
	server.use(
		sessionRoute(() => true),
		pinsRoute([pins]),
		downloadsRoute(),
		handshakeRoute(),
	);
	renderApp("/");
	const user = userEvent.setup();
	await user.click(
		await screen.findByRole("img", { name: pins[0]?.description }),
	);
	return { user, dialog: await screen.findByRole("dialog") };
}

/** A drag on the image, in two moves so that only their sum crosses the threshold. */
function drag(
	dialog: HTMLElement,
	x: number,
	y: number,
	pointerType = "touch",
) {
	const target = within(dialog).getByRole("img");
	const at = (share: number) => ({
		pointerId: 1,
		pointerType,
		clientX: 200 + x * share,
		clientY: 300 + y * share,
	});
	fireEvent.pointerDown(target, at(0));
	fireEvent.pointerMove(target, at(0.5));
	fireEvent.pointerMove(target, at(1));
	fireEvent.pointerUp(target, at(1));
}

/** The images the document asks the browser to load ahead. */
function preloaded() {
	return [
		...document.head.querySelectorAll('link[rel="preload"][as="image"]'),
	].map((link) => link.getAttribute("href"));
}

afterEach(() => vi.unstubAllGlobals());

describe("step through the grid from a pin", () => {
	it("Given an open pin, Then its buttons and the arrow keys step to its neighbours", async () => {
		const pins = [
			readyPin("a harbour at dusk"),
			readyPin("a cat asleep"),
			readyPin("a lighthouse"),
		];
		const { user, dialog } = await openTheFirst(pins);

		expect(
			within(dialog).getByRole("button", { name: m.pin_previous() }),
		).toBeDisabled();
		const next = within(dialog).getByRole("button", { name: m.pin_next() });
		await user.click(next);
		expect(within(dialog).getByText("a cat asleep")).toBeVisible();

		// The next button keeps the focus, and the arrows still reach the dialog through it.
		expect(next).toHaveFocus();
		await user.keyboard("{ArrowRight}");
		expect(within(dialog).getByText("a lighthouse")).toBeVisible();
		await user.keyboard("{ArrowLeft}");
		expect(within(dialog).getByText("a cat asleep")).toBeVisible();
	});

	it("Given an open pin, Then its neighbours' placeholders are loaded ahead and never their originals", async () => {
		const pins = [
			readyPin("a harbour at dusk"),
			readyPin("a cat asleep"),
			readyPin("a lighthouse"),
		];
		const [first, second, third] = pins.map((one) => String(one.image?.url));
		const { user, dialog } = await openTheFirst(pins);

		// The grid's own rendition, which jsdom's unmeasurable column makes the small one.
		expect(preloaded()).toContain(`${second}?size=SMALL`);
		expect(preloaded()).not.toContain(`${third}?size=SMALL`);
		await user.click(
			within(dialog).getByRole("button", { name: m.pin_next() }),
		);

		expect(preloaded()).toEqual(
			expect.arrayContaining([`${first}?size=SMALL`, `${third}?size=SMALL`]),
		);
		expect(preloaded()).not.toContain(third);
	});

	it("Given an arrow with a modifier, or one already handled, Then the pin stays", async () => {
		const pins = [readyPin("a harbour at dusk"), readyPin("a cat asleep")];
		const { user, dialog } = await openTheFirst(pins);

		// Alt+→ is the browser's forward: it is not also a step.
		await user.keyboard("{Alt>}{ArrowRight}{/Alt}");
		expect(dialog).toHaveAccessibleName("a harbour at dusk");
		dialog.addEventListener("keydown", (event) => event.preventDefault());
		await user.keyboard("{ArrowRight}");
		expect(dialog).toHaveAccessibleName("a harbour at dusk");
	});

	it("Given the form, Then nothing steps and the arrows move the caret", async () => {
		const pins = [readyPin("a harbour at dusk"), readyPin("a cat asleep")];
		const { user, dialog } = await openTheFirst(pins);

		await user.click(
			within(dialog).getByRole("button", { name: m.edit_pin() }),
		);
		expect(
			within(dialog).queryByRole("button", { name: m.pin_previous() }),
		).toBeNull();
		expect(
			within(dialog).queryByRole("button", { name: m.pin_next() }),
		).toBeNull();
		const description = within(dialog).getByRole<HTMLInputElement>("textbox", {
			name: m.description(),
		});
		await user.click(description);
		description.setSelectionRange(0, 0);
		await user.keyboard("{ArrowRight}");

		expect(description.selectionStart).toBe(1);
		expect(dialog).toHaveAccessibleName("a harbour at dusk");
	});

	it("Given the last loaded pin, Then next asks for the page the grid has not reached", async () => {
		// The grid's sentinel is never reached, so only the viewer can ask for the second page.
		vi.stubGlobal(
			"IntersectionObserver",
			class {
				observe() {}
				unobserve() {}
				disconnect() {}
			},
		);
		const first = readyPin("a harbour at dusk");
		const second = readyPin("a cat asleep");
		let answer = () => {};
		const held = new Promise<void>((resolve) => (answer = resolve));
		const cursors: (string | null)[] = [];
		server.use(
			sessionRoute(() => true),
			pinsRoute([[first], [second]], (request) => {
				const cursor = new URL(request.url).searchParams.get("cursor");
				cursors.push(cursor);
				return cursor === null ? undefined : held;
			}),
			downloadsRoute(),
			handshakeRoute(),
		);
		renderApp("/");
		const user = userEvent.setup();
		await user.click(
			await screen.findByRole("img", { name: first.description }),
		);
		const dialog = await screen.findByRole("dialog");
		expect(cursors).toEqual([null]);

		const next = within(dialog).getByRole("button", { name: m.pin_next() });
		await user.click(next);
		await waitFor(() => expect(cursors).toEqual([null, "1"]));
		expect(next).toBeDisabled();
		answer();

		expect(await within(dialog).findByText(second.description)).toBeVisible();
	});

	it("Given a touch swipe to the left, Then the next pin shows", async () => {
		const { dialog } = await openTheFirst([
			readyPin("a harbour at dusk"),
			readyPin("a cat asleep"),
		]);

		drag(dialog, -60, 0);

		expect(within(dialog).getByText("a cat asleep")).toBeVisible();
	});

	it("Given a drag too short, too steep or by a mouse, Then the same pin stays", async () => {
		const { dialog } = await openTheFirst([
			readyPin("a harbour at dusk"),
			readyPin("a cat asleep"),
		]);

		drag(dialog, -30, 0);
		drag(dialog, -60, 80);
		// A mouse drags the image out of the page rather than stepping.
		drag(dialog, -60, 0, "mouse");

		expect(within(dialog).getByText("a harbour at dusk")).toBeVisible();
	});
});
