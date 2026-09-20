import { Button, Checkbox } from "@heroui/react"
import { useState, type ReactNode } from "react"
import type { Key, Selection } from "react-aria-components"
import { m } from "../paraglide/messages.js"

/**
 * The selection a `GridList` reports, held as the keys of the rows it still shows: a key left
 * behind by a row the gesture has just taken away would keep the bar up over nothing.
 */
export function useSelection(rows: readonly { id: string }[]) {
  const [selected, setSelected] = useState<Set<Key>>(new Set())

  return {
    ids: [...selected].map(String).filter((id) => rows.some((row) => row.id === id)),
    clear: () => setSelected(new Set()),
    props: {
      selectionMode: "multiple" as const,
      selectedKeys: selected,
      // `all` is the keyboard's select-all, resolved here against the rows loaded rather than
      // carried as a word that would silently take in every page loaded after it.
      onSelectionChange: (keys: Selection) =>
        setSelected(keys === "all" ? new Set(rows.map((row) => row.id)) : new Set(keys)),
    },
  }
}

/**
 * The tick a row or a tile carries, wired to its `GridList` and named after its row by
 * `slot="selection"`. Hidden with `opacity` and never `display`, so it stays focusable while out of
 * sight; the last rule is for a browser reporting no hover, where nothing would reveal it.
 */
export function SelectionTick({ shown, className }: { shown: boolean; className?: string }) {
  return (
    <Checkbox
      slot="selection"
      className={`${shown ? "" : "opacity-0"} transition-opacity group-hover:opacity-100 group-focus-within:opacity-100 [@media(hover:none)]:opacity-100 ${className ?? ""}`}
    >
      <Checkbox.Content>
        <Checkbox.Control>
          <Checkbox.Indicator />
        </Checkbox.Control>
      </Checkbox.Content>
    </Checkbox>
  )
}

/**
 * The bar a grid raises while its selection is not empty. The gestures are the screen's own
 * (specification 2026-09-20, decision O); what the bar holds is the count and the way out. Sticky,
 * because the bin's tabs scroll around it where the catalogue's grid scrolls under it.
 */
export function SelectionBar({
  count,
  clear,
  children,
}: {
  count: number
  clear: () => void
  children: ReactNode
}) {
  if (count === 0) return null

  return (
    <div
      role="toolbar"
      aria-label={m.selection()}
      className="sticky top-0 z-20 flex flex-wrap items-center gap-2 rounded-lg bg-surface px-3 py-2 shadow-surface"
    >
      <span className="font-medium">{m.selected_count({ count })}</span>
      {children}
      {/* Last and apart: every other control here writes to the account, and this one only
          gives the selection up. Escape does the same from inside the grid. */}
      <Button variant="ghost" className="ms-auto" onPress={clear}>
        {m.clear_selection()}
      </Button>
    </div>
  )
}
