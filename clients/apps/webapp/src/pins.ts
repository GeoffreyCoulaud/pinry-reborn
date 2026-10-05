import type { Schemas } from "@pinry-reborn/auth";
import {
	type InfiniteData,
	skipToken,
	useInfiniteQuery,
	useMutation,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { auth, bodyOf } from "./api";
import type { PinSort } from "./lib/sorts";
import { removePins, replacePins } from "./lib/tiles";
import { rereadSettledPins } from "./media";

export type Pin = Schemas["PinOutputDto"];
export type PinPage = Schemas["PinListOutputDto"];
export type PinUpdate = Schemas["PinUpdateInputDto"];
export type Duplicate = Schemas["PinDuplicateOutputDto"];
export type PinMerge = Schemas["PinMergeInputDto"];

const PAGE_SIZE = 40;

/** Enough names to choose from without a scroll, the field offering them under the input. */
const TAG_SUGGESTIONS = 8;

const PINS = ["pins"];
const BOARDS = ["boards"];
// Not under `PINS`, whose writers read every entry there as a catalogue.
const DUPLICATES = ["duplicates"];

/**
 * The catalogue, one page at a time, in the order the API sorts it, or one board's share of it:
 * `GET /api/v1/boards/{boardId}/pins` has the signature of `GET /api/v1/pins` (specification
 * 2026-09-20, 2.5).
 * Every page loaded is kept: a cap on the query drops pages nothing reloads, and what holds the
 * grid's memory is the virtualiser, which mounts the visible tiles alone (ADR 0033).
 */
export function usePins(sort: PinSort, boardId?: string, term?: string) {
	return useInfiniteQuery({
		// The board, the order and the term are all part of the key: two catalogues sharing one would
		// serve either's pages under the other, and the grid would show a page it never requested. A
		// term changed also restarts the query with no cursor, which is what keeps a cursor with the
		// `q` it was minted under (specification 2026-09-21, section 7).
		queryKey: ["pins", boardId ?? null, sort, term ?? null],
		queryFn: async ({ pageParam }) => {
			// An absent `q` is omitted rather than sent empty, the route refusing a blank one
			// (decision C); `openapi-fetch` drops an undefined parameter.
			const query = { cursor: pageParam, pageSize: PAGE_SIZE, sort, q: term };
			const answer =
				boardId === undefined
					? await auth.client.GET("/api/v1/pins", { params: { query } })
					: await auth.client.GET("/api/v1/boards/{boardId}/pins", {
							params: { query, path: { boardId } },
						});
			return bodyOf(answer, "the pins");
		},
		// The cursor is opaque: it is read from a response and sent back unchanged (contract 3.0.0).
		initialPageParam: undefined as string | undefined,
		getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
	});
}

/**
 * The whole pin in one request. A field left out is refused rather than read as unchanged, so the
 * form sends what it read back (docs/adr/0038-one-route-writes-a-pin.md), and the pin is then
 * written into the pages the grid already holds rather than reloaded (decision P).
 */
export function useUpdatePin() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async ({ pinId, body }: { pinId: string; body: PinUpdate }) => {
			const saved = bodyOf(
				await auth.client.PUT("/api/v1/pins/{pinId}", {
					params: { path: { pinId } },
					body,
				}),
				"the pin",
			);
			// A pin written and then not read back is still written, so the catalogue answers for the
			// reread rather than the mutation failing over a pin the user has saved.
			await rereadSettledPins(queryClient, [pinId]).catch(() =>
				queryClient.invalidateQueries({ queryKey: PINS }),
			);
			// An edit is also a membership write: a board the pin left keeps no tile of it, the tile
			// having just been written back into every catalogue by the reread above.
			const held = new Set(saved.boards.map((board) => board.id));
			queryClient.setQueriesData<InfiniteData<PinPage>>(
				{
					queryKey: PINS,
					predicate: ({ queryKey }) =>
						typeof queryKey[1] === "string" && !held.has(queryKey[1]),
				},
				(catalogue) =>
					catalogue === undefined
						? catalogue
						: { ...catalogue, pages: removePins(catalogue.pages, [pinId]) },
			);
			// The counts a board carries are what the memberships just moved.
			await queryClient.invalidateQueries({ queryKey: BOARDS });
			// A duplicate opened from a list may be a pin no catalogue holds, and the list is what shows it.
			await queryClient.invalidateQueries({ queryKey: DUPLICATES });
		},
	});
}

/**
 * The delete, which sends the pins to the bin rather than away (decision R), in bulk so block 90's
 * selection sends the same shape. The tiles leave the pages the grid already holds rather than the
 * catalogue being reloaded (decision P), and every board and order is its own cached catalogue, so
 * the write reaches each of them rather than the one key this screen happens to hold.
 */
export function useRecyclePins() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (pinIds: readonly string[]) => {
			const { response } = await auth.client.DELETE("/api/v1/pins", {
				body: { pinIds: [...pinIds] },
			});
			if (!response.ok) {
				throw new Error(`The API kept the pins: ${response.status}.`);
			}
			queryClient.setQueriesData<InfiniteData<PinPage>>(
				{ queryKey: PINS },
				(catalogue) =>
					catalogue === undefined
						? catalogue
						: { ...catalogue, pages: removePins(catalogue.pages, pinIds) },
			);
			// A board counts the pins it holds that are still active, so a recycled one moves it.
			await queryClient.invalidateQueries({ queryKey: BOARDS });
			// A recycled pin's pairs are hidden (specification 2026-10-05, decision J).
			await queryClient.invalidateQueries({ queryKey: DUPLICATES });
		},
	});
}

/** A pin's likely duplicates, the rejected ones included, or nothing asked for no pin. */
export function useDuplicates(pinId: string | undefined) {
	return useQuery({
		queryKey: [...DUPLICATES, pinId],
		queryFn:
			pinId === undefined
				? skipToken
				: async () =>
						bodyOf(
							await auth.client.GET("/api/v1/pins/{pinId}/duplicates", {
								params: { path: { pinId } },
							}),
							"the duplicates",
						).duplicates,
	});
}

/** Rejects a duplicate or restores it. Both pins' markers move with it, so both are read again. */
export function useRejectDuplicate(pinId: string) {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async ({
			otherPinId,
			rejected,
		}: {
			otherPinId: string;
			rejected: boolean;
		}) => {
			bodyOf(
				await auth.client.PUT("/api/v1/pins/{pinId}/duplicates/{otherPinId}", {
					params: { path: { pinId, otherPinId } },
					body: { rejected },
				}),
				"the duplicate",
			);
			await queryClient.invalidateQueries({ queryKey: DUPLICATES });
			await rereadSettledPins(queryClient, [pinId, otherPinId]).catch(() =>
				queryClient.invalidateQueries({ queryKey: PINS }),
			);
		},
	});
}

/**
 * Merges a group into the pin it keeps, which the API answers with (specification 2026-10-05,
 * decision H). The absorbed pins leave every catalogue and the kept one is written in, unreloaded.
 */
export function useMergePins() {
	const queryClient = useQueryClient();
	return useMutation({
		mutationFn: async (body: PinMerge) => {
			const kept = bodyOf(
				await auth.client.POST("/api/v1/pins/merges", { body }),
				"the merge",
			);
			queryClient.setQueriesData<InfiniteData<PinPage>>(
				{ queryKey: PINS },
				(catalogue) =>
					catalogue === undefined
						? catalogue
						: {
								...catalogue,
								pages: removePins(catalogue.pages, body.absorbedPinIds).map(
									(page) => ({ ...page, pins: replacePins(page.pins, [kept]) }),
								),
							},
			);
			// The kept pin gained the absorbed pins' boards, whose counts lost them.
			await queryClient.invalidateQueries({ queryKey: BOARDS });
			await queryClient.invalidateQueries({ queryKey: DUPLICATES });
			return kept;
		},
	});
}

/**
 * The author's own tags, matched as the server matches them: a name is one identity per author
 * under an ASCII fold, so a spelling it already holds comes back rather than being invented here.
 */
export function useTagSearch(query: string) {
	const asked = query.trim();
	return useQuery({
		queryKey: ["tags", asked],
		// The route refuses a blank `q`, and an empty field is not a search.
		enabled: asked !== "",
		queryFn: async () => {
			const params = { query: { q: asked, limit: TAG_SUGGESTIONS } };
			const body = bodyOf(
				await auth.client.GET("/api/v1/tags/search", { params }),
				"the tags",
			);
			return body.results.map((result) => result.tag.name);
		},
	});
}
