import { Navigate, useParams, useSearch } from "@tanstack/react-router"
import { useBoards } from "../boards"
import { AppHeader } from "../components/AppHeader"
import { AppNav } from "../components/AppNav"
import { PinGrid } from "../components/PinGrid"
import { SearchField } from "../components/SearchField"
import { SortSelect } from "../components/SortSelect"
import { PIN_SORTS } from "../lib/sorts"
import { m } from "../paraglide/messages.js"
import { useSession } from "../session"

/**
 * One board's pins, in the grid the home screen renders. The drop that creates a pin is not here:
 * a board is not a place a drop files one (specification 2026-09-20, section 5).
 */
export function Board() {
  const { boardId } = useParams({ from: "/boards/$boardId" })
  const { sort, q } = useSearch({ from: "/boards/$boardId" })
  const session = useSession()
  // The list arrives whole and is the query the boards screen already holds, so a board opened
  // from that screen costs no request of its own (2.7).
  const boards = useBoards()
  const board = boards.data?.find((one) => one.id === boardId)

  if (session.isPending) return null
  if (session.isError) return <p role="alert">{m.session_unreadable()}</p>
  if (!session.data) return <Navigate to="/sign-in" />

  const heading = board?.name ?? m.boards()
  // An address is whatever the bar holds. A board the account does not hold says so where the
  // grid would be, so the screen keeps the navigation that leads back out of it.
  const unknown = boards.isSuccess && board === undefined

  return (
    <main className="flex h-screen flex-col gap-4 px-4 pt-4">
      <AppHeader
        heading={heading}
        search={!unknown && <SearchField term={q} boardName={heading} />}
      >
        <AppNav />
        {!unknown && <SortSelect value={sort} values={PIN_SORTS} />}
      </AppHeader>
      {board?.description && <p className="text-muted">{board.description}</p>}
      {/* Full bleed: the scrollbar belongs to the viewport edge, not inside the shell's padding. */}
      <div className="-mx-4 min-h-0 flex-1">
        {unknown ? (
          <p role="alert" className="px-4">
            {m.board_unknown()}
          </p>
        ) : (
          <PinGrid sort={sort} label={heading} boardId={boardId} term={q} />
        )}
      </div>
    </main>
  )
}
