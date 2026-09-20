import type { Schemas } from "@pinry-reborn/auth"
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import type { RecycledPinSort } from "./lib/sorts"

export type RecycledBoard = Schemas["RecycledBoardDto"]

const PAGE_SIZE = 40

const RECYCLED_PINS = ["recycled-pins"]
const RECYCLED_BOARDS = ["recycled-boards"]
const PINS = ["pins"]
const BOARDS = ["boards"]

/** The pins the bin holds, paged as the catalogue is and ordered by what left the grid last. */
export function useRecycledPins(sort: RecycledPinSort) {
  return useInfiniteQuery({
    queryKey: [...RECYCLED_PINS, sort],
    queryFn: async ({ pageParam }) => {
      const params = { query: { cursor: pageParam, pageSize: PAGE_SIZE, sort } }
      return bodyOf(await auth.client.GET("/api/v1/pins/recycled", { params }), "the bin")
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.pagination.nextCursor ?? undefined,
  })
}

/** The boards the bin holds, whole: the route takes no cursor and no sort (specification 2026-09-20, 2.5). */
export function useRecycledBoards() {
  return useQuery({
    queryKey: RECYCLED_BOARDS,
    queryFn: async () => bodyOf(await auth.client.GET("/api/v1/boards/recycled"), "the bin").boards,
  })
}

/**
 * A write to a bin and the rereads it owes. The bin is not the grid: its pages are reloaded rather
 * than edited in place, decision P being written for the catalogue a user scrolls, and a restore
 * is what puts a row back into the list or the grid it came from.
 */
function useBinWrite<Subject>(
  write: (subject: Subject) => Promise<{ response: Response }>,
  reread: readonly string[][],
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (subject: Subject) => {
      const { response } = await write(subject)
      if (!response.ok) throw new Error(`The API refused the bin: ${response.status}.`)
      await Promise.all(reread.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
    },
  })
}

/** In bulk, which is what block 90's selection will send it: one row is a list of one. */
export function useRestorePins() {
  return useBinWrite(
    (pinIds: readonly string[]) =>
      auth.client.POST("/api/v1/pins/recycled/restore", { body: { pinIds: [...pinIds] } }),
    // The boards too: a board counts the pins it holds that are active, and a restore moves that.
    [RECYCLED_PINS, PINS, BOARDS],
  )
}

export function useRestoreBoards() {
  return useBinWrite(
    (boardIds: readonly string[]) =>
      auth.client.POST("/api/v1/boards/recycled/restore", { body: { boardIds: [...boardIds] } }),
    [RECYCLED_BOARDS, BOARDS],
  )
}

/** One row at a time and gone for good: what decision F puts in bulk is the restore, not this. */
export function useDeletePinForGood() {
  return useBinWrite(
    (pinId: string) =>
      auth.client.DELETE("/api/v1/pins/recycled/{pinId}", { params: { path: { pinId } } }),
    [RECYCLED_PINS],
  )
}

export function useDeleteBoardForGood() {
  return useBinWrite(
    (boardId: string) =>
      auth.client.DELETE("/api/v1/boards/recycled/{boardId}", { params: { path: { boardId } } }),
    [RECYCLED_BOARDS],
  )
}

/** Bodyless, and that is what it means: everything the bin holds (ADR 0039, decision 4). */
export function useEmptyPinBin() {
  return useBinWrite(() => auth.client.DELETE("/api/v1/pins/recycled"), [RECYCLED_PINS])
}

export function useEmptyBoardBin() {
  return useBinWrite(() => auth.client.DELETE("/api/v1/boards/recycled"), [RECYCLED_BOARDS])
}
