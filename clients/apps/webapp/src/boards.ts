import type { Schemas } from "@pinry-reborn/auth"
import { useMutation, useQuery, useQueryClient, type InfiniteData } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import { removePins } from "./lib/tiles"
import type { PinPage } from "./pins"

export type Board = Schemas["BoardOutputDto"]
export type BoardInput = Schemas["BoardInputDto"]

/** The pins a gesture files under one board, or takes out of it, in one call (ADR 0039). */
type Membership = { boardId: string; pinIds: readonly string[] }

const BOARDS = ["boards"]

/** The board's own catalogue, as `usePins` keys it: `["pins", boardId, sort]`. */
const catalogueOf = (boardId: string) => ["pins", boardId]

/**
 * A board the create and the rename refused. 409 is a name this account already holds, which is the
 * user's to fix; on any other write of this file 409 means a recycled pin, so they throw a plain
 * `Error` rather than a flag that would read as a name taken.
 */
export class BoardRefusal extends Error {
  readonly nameTaken: boolean
  constructor(status: number) {
    super(`The API refused the board: ${status}.`)
    this.nameTaken = status === 409
  }
}

function saved(answer: { data?: Board; response: Response }): Board {
  if (answer.data === undefined) throw new BoardRefusal(answer.response.status)
  return answer.data
}

/**
 * Every board the account holds, in one request: `GET /api/v1/boards` takes no cursor and a board
 * carries no cover, so there is no page to chase and nothing to load lazily (specification
 * 2026-09-20, 2.7).
 */
export function useBoards() {
  return useQuery({
    queryKey: BOARDS,
    queryFn: async () => bodyOf(await auth.client.GET("/api/v1/boards"), "the boards").boards,
  })
}

/**
 * A write and the reread that follows it. The list is one query and not a catalogue paged through,
 * so invalidating it costs the one request it costs to show it (decision P is the grid's rule).
 */
function useBoardWrite<Written>(write: (written: Written) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: write,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: BOARDS }),
  })
}

export function useCreateBoard() {
  return useBoardWrite(async (body: BoardInput) =>
    saved(await auth.client.POST("/api/v1/boards", { body })),
  )
}

/** The whole board, name and description alike: `PUT /api/v1/boards/{boardId}` replaces it. */
export function useSaveBoard() {
  return useBoardWrite(async ({ boardId, body }: { boardId: string; body: BoardInput }) =>
    saved(await auth.client.PUT("/api/v1/boards/{boardId}", { params: { path: { boardId } }, body })),
  )
}

/** A soft delete: the board goes to the recycle bin, which block 80 gives a screen. */
export function useDeleteBoard() {
  return useBoardWrite(async (boardId: string) => {
    const { response } = await auth.client.DELETE("/api/v1/boards/{boardId}", {
      params: { path: { boardId } },
    })
    if (!response.ok) throw new Error(`The API kept the board: ${response.status}.`)
  })
}

/**
 * The selection filed under a board. The pins stay in every catalogue that already shows them, a
 * board being one more place they appear; what changes is the board's own, which is read again the
 * next time it is shown, and the pin counts the boards screen carries.
 */
export function useAddPinsToBoard() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ boardId, pinIds }: Membership) => {
      const { response } = await auth.client.POST("/api/v1/boards/{boardId}/pins", {
        params: { path: { boardId } },
        body: { pinIds: [...pinIds] },
      })
      if (!response.ok) throw new Error(`The API filed nothing: ${response.status}.`)
      await queryClient.invalidateQueries({ queryKey: catalogueOf(boardId) })
      await queryClient.invalidateQueries({ queryKey: BOARDS })
    },
  })
}

/**
 * The selection taken out of the board whose grid it was read on. The tiles leave the pages that
 * grid already holds rather than it being reloaded (decision P), every order being its own key.
 */
export function useRemovePinsFromBoard() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ boardId, pinIds }: Membership) => {
      const { response } = await auth.client.DELETE("/api/v1/boards/{boardId}/pins", {
        params: { path: { boardId } },
        body: { pinIds: [...pinIds] },
      })
      if (!response.ok) throw new Error(`The API took nothing out: ${response.status}.`)
      queryClient.setQueriesData<InfiniteData<PinPage>>(
        { queryKey: catalogueOf(boardId) },
        (catalogue) =>
          catalogue === undefined
            ? catalogue
            : { ...catalogue, pages: removePins(catalogue.pages, pinIds) },
      )
      await queryClient.invalidateQueries({ queryKey: BOARDS })
    },
  })
}
