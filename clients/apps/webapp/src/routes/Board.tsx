import { Navigate, useParams, useSearch } from "@tanstack/react-router"
import { useBoards } from "../boards"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { PinGrid } from "../components/PinGrid"
import { SortSelect } from "../components/SortSelect"
import { PIN_SORTS } from "../lib/sorts"
import { m } from "../paraglide/messages.js"
import { useSession } from "../session"

/**
 * One board's pins, in the grid the home screen renders. The drop that creates a pin is not here:
 * a board is not a place a drop files one (specification, section 5).
 */
export function Board() {
  const { boardId } = useParams({ from: "/boards/$boardId" })
  const { sort } = useSearch({ from: "/boards/$boardId" })
  const session = useSession()
  // The list arrives whole and is the query the boards screen already holds, so a board opened
  // from that screen costs no request of its own (specification 2.7).
  const boards = useBoards()
  const board = boards.data?.find((one) => one.id === boardId)

  if (session.isPending) return null
  if (session.isError) return <p role="alert">{m.session_unreadable()}</p>
  if (!session.data) return <Navigate to="/sign-in" />
  // An address is whatever the bar holds: a board the account does not hold says so, rather than
  // heading an empty screen with a grid the API refuses.
  if (boards.isSuccess && board === undefined) return <p role="alert">{m.board_unknown()}</p>

  const heading = board?.name ?? m.boards()

  return (
    <main className="flex h-screen flex-col gap-4 px-4 pt-4">
      <AppHeader heading={heading}>
        <AppNav />
        <SortSelect value={sort} values={PIN_SORTS} />
      </AppHeader>
      {board?.description && <p className="text-muted">{board.description}</p>}
      {/* Full bleed: the scrollbar belongs to the viewport edge, not inside the shell's padding. */}
      <div className="-mx-4 min-h-0 flex-1">
        <PinGrid sort={sort} label={heading} boardId={boardId} />
      </div>
    </main>
  )
}
