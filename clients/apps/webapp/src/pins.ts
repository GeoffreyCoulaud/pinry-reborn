import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery } from "@tanstack/react-query"
import { auth } from "./api"
import type { PinSort } from "./lib/sorts"

export type Pin = Schemas["PinOutputDto"]
export type PinPage = Schemas["PinListOutputDto"]

const PAGE_SIZE = 40

/**
 * The catalogue, one page at a time, in the order the API sorts it. Every page loaded is kept:
 * a cap on the query drops pages nothing reloads, and what holds the grid's memory is the
 * virtualiser, which mounts the visible tiles alone (ADR 0033).
 */
export function usePins(sort: PinSort) {
  return useInfiniteQuery({
    // The order is part of the key: two orders sharing one would serve either's pages under
    // the other, and the grid would show a page it never requested.
    queryKey: ["pins", sort],
    queryFn: async ({ pageParam }) => {
      const { data, response } = await auth.client.GET("/api/v1/pins", {
        params: { query: { cursor: pageParam, pageSize: PAGE_SIZE, sort } },
      })
      if (data === undefined) throw new Error(`The API refused the pins: ${response.status}.`)
      return data
    },
    // The cursor is opaque: it is read from a response and sent back unchanged (contract 3.0.0).
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  })
}
