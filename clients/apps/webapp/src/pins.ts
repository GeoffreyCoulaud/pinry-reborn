import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import { rereadSettledPins } from "./images"
import type { PinSort } from "./lib/sorts"

export type Pin = Schemas["PinOutputDto"]
export type PinPage = Schemas["PinListOutputDto"]
export type PinUpdate = Schemas["PinUpdateInputDto"]

const PAGE_SIZE = 40

/** Enough names to choose from without a scroll, the field offering them under the input. */
const TAG_SUGGESTIONS = 8

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

/**
 * The whole pin in one request. A field left out is refused rather than read as unchanged, so the
 * form sends what it read back (docs/adr/0038-one-route-writes-a-pin.md), and the pin is then
 * written into the pages the grid already holds rather than reloaded (decision P).
 */
export function useUpdatePin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ pinId, body }: { pinId: string; body: PinUpdate }) => {
      bodyOf(
        await auth.client.PUT("/api/v1/pins/{pinId}", { params: { path: { pinId } }, body }),
        "the pin",
      )
      await rereadSettledPins(queryClient, [pinId])
    },
  })
}

/**
 * The author's own tags, matched as the server matches them: a name is one identity per author
 * under an ASCII fold, so a spelling it already holds comes back rather than being invented here.
 */
export function useTagSearch(query: string) {
  const asked = query.trim()
  return useQuery({
    queryKey: ["tags", asked],
    // The route refuses a blank `q`, and an empty field is not a search.
    enabled: asked !== "",
    queryFn: async () => {
      const params = { query: { q: asked, limit: TAG_SUGGESTIONS } }
      const body = bodyOf(await auth.client.GET("/api/v1/tags/search", { params }), "the tags")
      return body.results.map((result) => result.tag.name)
    },
  })
}
