import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { m } from "../paraglide/messages.js";
import {
	downloadsRoute,
	dropOf,
	handshakeRoute,
	onePinPage,
	readyPin,
	refused,
	renderApp,
	sessionRoute,
} from "../test/app";
import { server } from "../test/server";

const created = readyPin("a cat running");

/** The routes every case shares, the upload answered as the case decides. */
function routes(upload: () => Response, maxVideoBytes?: number) {
	server.use(
		sessionRoute(() => true),
		handshakeRoute({ maxVideoBytes }),
		downloadsRoute(),
		onePinPage(() => [created]),
		http.post("/api/v1/pins", () =>
			HttpResponse.json(created, { status: 201 }),
		),
		http.put("/api/v1/pins/:pinId/media", upload),
	);
}

const stored = () =>
	HttpResponse.json({ id: created.id, pinId: created.id }, { status: 201 });

/** The file dropped on the dialog's area, and the dialog itself once the drop is judged. */
async function dropOnTheDialog(file: File) {
	const user = userEvent.setup();
	renderApp("/");
	await user.click(await screen.findByRole("button", { name: m.create_pin() }));
	const dialog = await screen.findByRole("dialog", { name: m.create_pin() });
	fireEvent.drop(within(dialog).getByLabelText(m.drop_media()), dropOf([file]));
	return { user, dialog };
}

async function submit(
	user: ReturnType<typeof userEvent.setup>,
	dialog: HTMLElement,
) {
	const button = within(dialog).getByRole("button", { name: m.create_pin() });
	await waitFor(() => expect(button).toBeEnabled());
	await user.click(button);
}

afterEach(() => vi.restoreAllMocks());

describe("upload a video", () => {
	it("Given an MP4 dropped, Then it is previewed and sent without a decode", async () => {
		let sent = false;
		routes(() => {
			sent = true;
			return stored();
		});
		const decode = vi.spyOn(globalThis, "createImageBitmap");

		const { user, dialog } = await dropOnTheDialog(
			new File(["ok"], "cat.mp4", { type: "video/mp4" }),
		);
		expect(await within(dialog).findByText("cat.mp4")).toBeVisible();
		await waitFor(() => expect(dialog.querySelector("video")).not.toBeNull());
		await submit(user, dialog);

		await waitFor(() => expect(sent).toBe(true));
		// A browser decodes no video into a bitmap, so measuring one would refuse every video.
		expect(decode).not.toHaveBeenCalled();
	});

	it("Given a video heavier than the video bound, Then no request leaves", async () => {
		let sent = false;
		routes(() => {
			sent = true;
			return stored();
		}, 4);

		await dropOnTheDialog(
			new File(["more than four bytes"], "cat.mp4", { type: "video/mp4" }),
		);

		expect(await screen.findByRole("alert")).toHaveTextContent(
			m.file_too_heavy(),
		);
		expect(sent).toBe(false);
	});

	it("Given a file the browser names no type for, Then the server is the one that judges it", async () => {
		let sent = false;
		routes(() => {
			sent = true;
			return stored();
		});
		const decode = vi.spyOn(globalThis, "createImageBitmap");

		const { user, dialog } = await dropOnTheDialog(
			new File(["ok"], "cat.mkv", { type: "" }),
		);
		expect(await within(dialog).findByText("cat.mkv")).toBeVisible();
		await submit(user, dialog);

		await waitFor(() => expect(sent).toBe(true));
		expect(decode).not.toHaveBeenCalled();
	});

	it.each([
		[422, "MEDIA_TOO_LONG", m.media_too_long()],
		[415, "MEDIA_CODEC_UNSUPPORTED", m.media_codec_unsupported()],
		[422, "A_CODE_THIS_BUNDLE_LACKS", m.creation_refused()],
	])(
		"Given the upload refused with %i %s, Then the dialog says why",
		async (status, code, sentence) => {
			routes(() => refused(status, code));

			const { user, dialog } = await dropOnTheDialog(
				new File(["ok"], "cat.mp4", { type: "video/mp4" }),
			);
			expect(await within(dialog).findByText("cat.mp4")).toBeVisible();
			await submit(user, dialog);

			expect(await within(dialog).findByRole("alert")).toHaveTextContent(
				sentence,
			);
		},
	);
});
