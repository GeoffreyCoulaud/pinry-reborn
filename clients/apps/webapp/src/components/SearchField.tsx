import { SearchFieldGroup, SearchFieldInput, SearchFieldRoot, SearchFieldSearchIcon } from "@heroui/react"
import { Link, useNavigate } from "@tanstack/react-router"
import { useEffect, useState } from "react"
import { useDebounced } from "../debounce"
import { searchTermOr } from "../lib/searches"
import { m } from "../paraglide/messages.js"

/** The pause a field waits out before it asks the API, decision K's value for decision P's hook. */
const SEARCH_PAUSE_MS = 300

/**
 * The header's search. The term lives in the address of the screen the field is on, so a reload, a
 * bookmark and the back button all keep the search with nothing stored, and a board searches that
 * board (specification 2026-09-21, decisions K and L). `replace` keeps one history entry per
 * search rather than one per keystroke, and the pause keeps one request per search.
 */
export function SearchField({ term, boardName }: { term?: string; boardName?: string }) {
  const [typed, setTyped] = useState(term ?? "")
  const asked = searchTermOr(useDebounced(typed, SEARCH_PAUSE_MS))
  const navigate = useNavigate()

  useEffect(() => {
    // The address is the state, so what the field holds is written to it and read back from it:
    // a term already there is not written again, which is what stops the loop.
    if (asked === term) return
    void navigate({ to: ".", search: (previous) => ({ ...previous, q: asked }), replace: true })
  }, [asked, term, navigate])

  const name = boardName === undefined ? m.search_placeholder() : m.search_in_board({ name: boardName })

  return (
    <div className="flex min-w-48 flex-1 flex-col gap-1 sm:max-w-sm">
      {/* No visible label: the bar has no room for one, and the placeholder is the name a reader
          hears, which is what says whether the search reaches a board or the whole collection. */}
      <SearchFieldRoot aria-label={name} value={typed} onChange={setTyped}>
        <SearchFieldGroup>
          <SearchFieldSearchIcon />
          <SearchFieldInput placeholder={name} />
        </SearchFieldGroup>
      </SearchFieldRoot>
      {boardName !== undefined && term !== undefined && (
        <Link to="/" search={{ q: term }} className="text-sm text-muted hover:underline">
          {m.search_everywhere()}
        </Link>
      )}
    </div>
  )
}
