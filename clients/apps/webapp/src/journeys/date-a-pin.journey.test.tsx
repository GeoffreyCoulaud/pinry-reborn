import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
import { getLocale } from "../paraglide/runtime.js";
import type { Pin } from "../pins";
import {
	boardRoutes,
	downloadsRoute,
	handshakeRoute,
	pinsRoute,
	readyPin,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

/** The catalogue, the write and the reread, each write's body recorded. */
function account(original: Pin, bodies: Partial<Pin>[]) {
	server.use(
		sessionRoute(() => true),
		pinsRoute([[original]]),
		http.put("/api/v1/pins/:pinId", async ({ request }) => {
			const body = (await request.json()) as Partial<Pin>;
			bodies.push(body);
			return HttpResponse.json({ ...original, ...body });
		}),
		http.get("/api/v1/pins/:pinId", () => HttpResponse.json(original)),
		...boardRoutes([]),
		downloadsRoute(),
		handshakeRoute(),
	);
}

/** The pin's form, opened from its tile. */
async function openTheForm(
	user: ReturnType<typeof userEvent.setup>,
	description: string,
) {
	await user.click(await screen.findByRole("img", { name: description }));
	await user.click(await screen.findByRole("button", { name: m.edit_pin() }));
	return screen.getByRole("dialog");
}

describe("date a pin", () => {
	it("Given a pin with no date, When a day is chosen, Then 00:00 in the browser's zone is sent", async () => {
		const original = readyPin("a harbour at dusk");
		const bodies: Partial<Pin>[] = [];
		account(original, bodies);
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, original.description);
		const time = within(dialog).getByLabelText(m.published_time());
		expect(time).toBeDisabled();
		await user.type(
			within(dialog).getByLabelText(m.published_day()),
			"2026-01-15",
		);

		expect(time).toBeEnabled();
		expect(time).toHaveValue("00:00");
		const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
		expect(within(dialog).getByText(m.published_zone({ zone }))).toBeVisible();
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		await waitFor(() => expect(bodies).toHaveLength(1));
		expect(bodies[0]?.publishedAt).toBe(new Date(2026, 0, 15).toISOString());
	});

	it("Given a dated pin, When its date is cleared, Then the write sends none", async () => {
		const original = {
			...readyPin("a harbour at dusk"),
			publishedAt: "2019-05-01T12:00:00Z",
		};
		const bodies: Partial<Pin>[] = [];
		account(original, bodies);
		renderApp("/");
		const user = userEvent.setup();

		const dialog = await openTheForm(user, original.description);
		await user.click(
			within(dialog).getByRole("button", { name: m.published_clear() }),
		);

		expect(within(dialog).getByLabelText(m.published_day())).toHaveValue("");
		expect(within(dialog).getByLabelText(m.published_time())).toBeDisabled();
		expect(
			within(dialog).queryByRole("button", { name: m.published_clear() }),
		).toBeNull();
		await user.click(within(dialog).getByRole("button", { name: m.save() }));
		await waitFor(() => expect(bodies).toHaveLength(1));
		expect(bodies[0]?.publishedAt).toBeNull();
	});

	it("Given a dated pin, Then the dialog shows its date and time in the browser's zone", async () => {
		const original = {
			...readyPin("a harbour at dusk"),
			publishedAt: "2019-05-01T12:00:00Z",
		};
		account(original, []);
		renderApp("/");

		await userEvent
			.setup()
			.click(await screen.findByRole("img", { name: original.description }));

		const shown = new Intl.DateTimeFormat(getLocale(), {
			dateStyle: "medium",
			timeStyle: "short",
		}).format(new Date(original.publishedAt));
		expect(
			within(await screen.findByRole("dialog")).getByText(shown),
		).toBeVisible();
	});
});
