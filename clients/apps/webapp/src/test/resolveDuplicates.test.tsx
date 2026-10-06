import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { type Pin, useResolveDuplicates } from "../pins";
import { duplicateRoutes, type Pair, readyPin } from "./app";
import { server } from "./server";

describe("a group's resolution", () => {
	it("Given the open pin kept, a candidate merged and one rejected, Then one call answers the open pin, the merged pair goes and the rejected one stays rejected", async () => {
		const open = readyPin("a harbour at dusk");
		const merged = readyPin("the same harbour, smaller", 400, 300);
		const other = readyPin("a harbour elsewhere");
		const pairs: Pair[] = [
			{ pins: [open, merged], rejected: false },
			{ pins: [open, other], rejected: false },
		];
		server.use(...duplicateRoutes(pairs));
		const client = new QueryClient();
		const wrapper = ({ children }: { children: ReactNode }) => (
			<QueryClientProvider client={client}>{children}</QueryClientProvider>
		);
		const resolved: Pin[] = [];
		const { result } = renderHook(
			() => useResolveDuplicates(open.id, (kept) => resolved.push(kept)),
			{ wrapper },
		);

		result.current.mutate({
			[open.id]: "KEEP",
			[merged.id]: "MERGE",
			[other.id]: "REJECT",
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));
		expect(resolved).toEqual([open]);
		expect(pairs).toEqual([{ pins: [open, other], rejected: true }]);
	});
});
