import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery } from "@tanstack/react-query"
import { auth } from "./api"

export type Pin = Schemas["PinOutputDto"]
export type PinPage = Schemas["PinListOutputDto"]

const PAGE_SIZE = 40

/**
 * The catalogue, one page at a time, in the order the API sorts it. Every page loaded is kept:
 * a cap on the query drops pages nothing reloads, and what holds the grid's memory is the
 * virtualiser, which mounts the visible tiles alone (specification 4.7, question Y).
 */
export function usePins() {
  return useInfiniteQuery({
    queryKey: ["pins"],
    queryFn: async ({ pageParam }) => {
      const { data, response } = await auth.client.GET("/api/v1/pins", {
        params: { query: { cursor: pageParam, pageSize: PAGE_SIZE } },
      })
      if (data === undefined) throw new Error(`The API refused the pins: ${response.status}.`)
      return data
    },
    // The cursor is opaque: it is read from a response and sent back unchanged (contract 3.0.0).
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  })
}
