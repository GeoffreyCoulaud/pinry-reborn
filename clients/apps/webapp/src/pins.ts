import type { Schemas } from "@pinry-reborn/auth"
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import { rereadSettledPins } from "./images"
import type { PinSort } from "./lib/sorts"
import { removePins } from "./lib/tiles"

export type Pin = Schemas["PinOutputDto"]
export type PinPage = Schemas["PinListOutputDto"]
export type PinUpdate = Schemas["PinUpdateInputDto"]

const PAGE_SIZE = 40

/** Enough names to choose from without a scroll, the field offering them under the input. */
const TAG_SUGGESTIONS = 8

const PINS = ["pins"]
const BOARDS = ["boards"]

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
      const query = { cursor: pageParam, pageSize: PAGE_SIZE, sort, q: term }
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
      const saved = bodyOf(
        await auth.client.PUT("/api/v1/pins/{pinId}", { params: { path: { pinId } }, body }),
        "the pin",
      )
      // A pin written and then not read back is still written, so the catalogue answers for the
      // reread rather than the mutation failing over a pin the user has saved.
      await rereadSettledPins(queryClient, [pinId]).catch(() =>
        queryClient.invalidateQueries({ queryKey: PINS }),
      )
      // An edit is also a membership write: a board the pin left keeps no tile of it, the tile
      // having just been written back into every catalogue by the reread above.
      const held = new Set(saved.boards.map((board) => board.id))
      queryClient.setQueriesData<InfiniteData<PinPage>>(
        {
          queryKey: PINS,
          predicate: ({ queryKey }) => typeof queryKey[1] === "string" && !held.has(queryKey[1]),
        },
        (catalogue) =>
          catalogue === undefined
            ? catalogue
            : { ...catalogue, pages: removePins(catalogue.pages, [pinId]) },
      )
      // The counts a board carries are what the memberships just moved.
      await queryClient.invalidateQueries({ queryKey: BOARDS })
    },
  })
}

/**
 * The delete, which sends the pins to the bin rather than away (decision R), in bulk so block 90's
 * selection sends the same shape. The tiles leave the pages the grid already holds rather than the
 * catalogue being reloaded (decision P), and every board and order is its own cached catalogue, so
 * the write reaches each of them rather than the one key this screen happens to hold.
 */
export function useRecyclePins() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (pinIds: readonly string[]) => {
      const { response } = await auth.client.DELETE("/api/v1/pins", {
        body: { pinIds: [...pinIds] },
      })
      if (!response.ok) throw new Error(`The API kept the pins: ${response.status}.`)
      queryClient.setQueriesData<InfiniteData<PinPage>>({ queryKey: PINS }, (catalogue) =>
        catalogue === undefined
          ? catalogue
          : { ...catalogue, pages: removePins(catalogue.pages, pinIds) },
      )
      // A board counts the pins it holds that are still active, so a recycled one moves it.
      await queryClient.invalidateQueries({ queryKey: BOARDS })
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
