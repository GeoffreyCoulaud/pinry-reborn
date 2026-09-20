import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import type { PinSort } from "./lib/sorts"

export type Pin = Schemas["PinOutputDto"]
export type PinPage = Schemas["PinListOutputDto"]

const PAGE_SIZE = 40

/**
 * The catalogue, one page at a time, in the order the API sorts it, or one board's share of it:
 * `GET /api/v1/boards/{boardId}/pins` has the signature of `GET /api/v1/pins` (specification 2.5).
 * Every page loaded is kept: a cap on the query drops pages nothing reloads, and what holds the
 * grid's memory is the virtualiser, which mounts the visible tiles alone (ADR 0033).
 */
export function usePins(sort: PinSort, boardId?: string) {
  return useInfiniteQuery({
    // The board and the order are both part of the key: two catalogues sharing one would serve
    // either's pages under the other, and the grid would show a page it never requested.
    queryKey: ["pins", boardId ?? null, sort],
    queryFn: async ({ pageParam }) => {
      const query = { cursor: pageParam, pageSize: PAGE_SIZE, sort }
      const answer =
        boardId === undefined
          ? await auth.client.GET("/api/v1/pins", { params: { query } })
          : await auth.client.GET("/api/v1/boards/{boardId}/pins", {
              params: { query, path: { boardId } },
            })
      return bodyOf(answer, "the pins")
    },
    // The cursor is opaque: it is read from a response and sent back unchanged (contract 3.0.0).
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  })
}
