import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { m } from "../paraglide/messages.js";
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

/** Two people of one name, which only their addresses tell apart. */
const ADA = {
	name: "Ada",
	urls: ["https://art.example.test/ada", "https://ada.test/"],
};
const ANOTHER_ADA = { name: "Ada", urls: [] };

/** The catalogue, the write, the reread and the person search, each recorded. */
function account(
	original: Pin,
	record: { bodies: unknown[]; queries: (string | null)[] },
) {
	server.use(
		sessionRoute(() => true),
		pinsRoute([[original]]),
		http.put("/api/v1/pins/:pinId", async ({ request }) => {
			const body = (await request.json()) as Partial<Pin>;
			record.bodies.push(body);
			return HttpResponse.json({ ...original, ...body });
		}),
		http.get("/api/v1/pins/:pinId", () => HttpResponse.json(original)),
		http.get("/api/v1/persons/search", ({ request }) => {
			record.queries.push(new URL(request.url).searchParams.get("q"));
			return HttpResponse.json({
				results: [{ person: ADA }, { person: ANOTHER_ADA }],
			});
		}),
		...boardRoutes([]),
		downloadsRoute(),
		handshakeRoute(),
	);
}

function recorder() {
	return { bodies: [] as unknown[], queries: [] as (string | null)[] };
}

/** The app opened on the pin's form. */
async function openTheForm(original: Pin) {
	renderApp("/");
	const user = userEvent.setup();
	await user.click(
		await screen.findByRole("img", { name: original.description }),
	);
	await user.click(await screen.findByRole("button", { name: m.edit_pin() }));
	const dialog = screen.getByRole("dialog");
	const publisher = within(dialog).getByRole("combobox", {
		name: m.publisher(),
	});
	return { user, dialog, publisher };
}

describe("credit a pin's people", () => {
	it("Given a publisher typed, Then the write names it with no address and the creators as read", async () => {
		const original = { ...readyPin("a harbour at dusk"), creators: [ADA] };
		const record = recorder();
		account(original, record);
		const { user, dialog, publisher } = await openTheForm(original);

		await user.type(publisher, "  Harbour Weekly {Enter}");
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		// A name typed is a person with none; the creators left alone are sent as read.
		await waitFor(() => expect(record.bodies).toHaveLength(1));
		expect(record.bodies[0]).toEqual(
			expect.objectContaining({
				creators: [ADA],
				publisher: { name: "Harbour Weekly", urls: [] },
			}),
		);
	});

	it("Given a creator chosen and another entered, Then the write names each as chosen", async () => {
		const original = readyPin("a harbour at dusk");
		const record = recorder();
		account(original, record);
		const { user, dialog } = await openTheForm(original);

		const field = within(dialog).getByRole("textbox", { name: m.creators() });
		await user.type(field, "Ad");
		// The homonyms read apart by the hosts of their addresses.
		await user.click(
			await within(dialog).findByRole("button", {
				name: "Ada (art.example.test, ada.test)",
			}),
		);
		await user.type(field, "  Grace {Enter}");
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		// The suggestion keeps the addresses the server knows; a name entered is a person with none.
		await waitFor(() => expect(record.bodies).toHaveLength(1));
		expect(record.bodies[0]).toEqual(
			expect.objectContaining({
				creators: [ADA, { name: "Grace", urls: [] }],
			}),
		);
	});

	it("Given two creators entered, Then each is a chip inside the field, which says how to add one", async () => {
		const original = readyPin("a harbour at dusk");
		account(original, recorder());
		const { user, dialog } = await openTheForm(original);

		const field = within(dialog).getByRole("textbox", { name: m.creators() });
		await user.type(field, "Ada{Enter}Grace{Enter}");

		const chips = within(dialog).getByRole("grid", {
			name: m.creators_chosen(),
		});
		expect(within(chips).getAllByRole("row")).toHaveLength(2);
		expect(field.closest('[data-slot="input-group"]')).toContainElement(chips);
		expect(field).toHaveAccessibleDescription(m.enter_to_add());
	});

	it("Given a publisher chosen from a suggestion, Then the field holds it as its one value", async () => {
		const original = readyPin("a harbour at dusk");
		const record = recorder();
		account(original, record);
		const { user, dialog, publisher } = await openTheForm(original);

		await user.type(publisher, "Ad");
		// The homonyms read apart by the hosts of their addresses.
		await user.click(
			await screen.findByRole("option", {
				name: "Ada (art.example.test, ada.test)",
			}),
		);

		// A term typed in one go is asked for once.
		expect(record.queries).toEqual(["Ad"]);
		// One value, so no chip and nothing about adding another.
		expect(publisher).toHaveValue("Ada (art.example.test, ada.test)");
		expect(publisher).not.toHaveAccessibleDescription();
		expect(within(dialog).queryAllByRole("grid")).toHaveLength(0);
		await user.click(within(dialog).getByRole("button", { name: m.save() }));
		await waitFor(() => expect(record.bodies).toHaveLength(1));
		expect(record.bodies[0]).toEqual(
			expect.objectContaining({ publisher: ADA }),
		);
	});

	it("Given the publisher cleared, Then the write names none", async () => {
		const original = {
			...readyPin("a harbour at dusk"),
			publisher: { name: "Harbour Weekly", urls: [] },
		};
		const record = recorder();
		account(original, record);
		const { user, dialog, publisher } = await openTheForm(original);

		expect(publisher).toHaveValue("Harbour Weekly");
		await user.click(
			within(dialog).getByRole("button", { name: m.publisher_clear() }),
		);
		expect(publisher).toHaveValue("");
		await user.click(within(dialog).getByRole("button", { name: m.save() }));

		await waitFor(() => expect(record.bodies).toHaveLength(1));
		expect(record.bodies[0]).toEqual(
			expect.objectContaining({ publisher: null }),
		);
	});

	it("Given a pin crediting a creator with two addresses, Then the dialog links each by its host", async () => {
		const original = {
			...readyPin("a harbour at dusk"),
			publisher: { name: "Harbour Weekly", urls: [] },
			creators: [ADA],
		};
		account(original, { bodies: [], queries: [] });
		renderApp("/");

		await userEvent
			.setup()
			.click(await screen.findByRole("img", { name: original.description }));

		const dialog = await screen.findByRole("dialog");
		expect(within(dialog).getByText("Harbour Weekly")).toBeVisible();
		expect(within(dialog).getByText("Ada")).toBeVisible();
		const art = within(dialog).getByRole("link", { name: "art.example.test" });
		expect(art).toHaveAttribute("href", ADA.urls[0]);
		expect(art).toHaveAttribute("target", "_blank");
		expect(
			within(dialog).getByRole("link", { name: "ada.test" }),
		).toHaveAttribute("href", ADA.urls[1]);
	});
});
