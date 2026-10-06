import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { type Pin, useResolveDuplicates } from "../pins";
import { readyPin } from "./app";
import { server } from "./server";

describe("a group's resolution", () => {
	it("Given the open pin kept, a candidate merged and one rejected, Then one call under the open pin sends every decision and hands the answer on once", async () => {
		const open = readyPin("a harbour at dusk");
		const merged = readyPin("the same harbour, smaller", 400, 300);
		const other = readyPin("a harbour elsewhere");
		const decisions = {
			[open.id]: "KEEP",
			[merged.id]: "MERGE",
			[other.id]: "REJECT",
		} as const;
		const sent: { pinId: unknown; body: unknown }[] = [];
		server.use(
			http.post(
				"/api/v1/pins/:pinId/duplicates/resolutions",
				async ({ params, request }) => {
					sent.push({ pinId: params.pinId, body: await request.json() });
					return HttpResponse.json(open);
				},
			),
		);
		const client = new QueryClient();
		const wrapper = ({ children }: { children: ReactNode }) => (
			<QueryClientProvider client={client}>{children}</QueryClientProvider>
		);
		const resolved: Pin[] = [];
		const { result } = renderHook(
			() => useResolveDuplicates(open.id, (kept) => resolved.push(kept)),
			{ wrapper },
		);

		result.current.mutate(decisions);

		await waitFor(() => expect(result.current.isSuccess).toBe(true));
		expect(sent).toEqual([{ pinId: open.id, body: { decisions } }]);
		expect(resolved).toEqual([open]);
	});
});
