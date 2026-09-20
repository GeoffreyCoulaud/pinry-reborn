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
 * The tick a row or a tile carries. `slot="selection"` is what wires it to the `GridList` it sits
 * in: without it the list is selectable by the keyboard alone, and a pointer has no way in. The
 * slot also names it, after the row it belongs to, so nothing here writes a label of its own.
 */
export function SelectionTick({ className }: { className?: string }) {
  return (
    <Checkbox slot="selection" className={className}>
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
 * (decision O); what the bar holds itself is the count and the way out. Sticky, because the bin's
 * tabs scroll around it where the catalogue's grid scrolls under it.
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
