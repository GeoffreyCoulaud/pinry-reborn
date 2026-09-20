import type { Schemas } from "@pinry-reborn/auth"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"

export type Board = Schemas["BoardOutputDto"]
export type BoardInput = Schemas["BoardInputDto"]

const BOARDS = ["boards"]

/** A board the API refused. 409 is a name this account already holds, which is the user's to fix. */
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
 * carries no cover, so there is no page to chase and nothing to load lazily (specification 2.7).
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
    if (!response.ok) throw new BoardRefusal(response.status)
  })
}
