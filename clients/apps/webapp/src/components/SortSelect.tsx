import { ListBox, Select } from "@heroui/react"
import { useNavigate } from "@tanstack/react-router"
import { recycledPinSortOr, type RecycledPinSort } from "../lib/sorts"
import { m } from "../paraglide/messages.js"

const LABELS: Record<RecycledPinSort, () => string> = {
  CREATED_AT_ASC: m.sort_created_at_asc,
  CREATED_AT_DESC: m.sort_created_at_desc,
  DELETED_AT_DESC: m.sort_deleted_at_desc,
}

/**
 * The order lives in the address, so a reload and the back button both keep it with nothing
 * stored. The screen passes the orders it has: a board's grid and the bin do not offer the same,
 * and the route's own validator narrows what this one writes.
 */
export function SortSelect(props: { value: RecycledPinSort; values: readonly RecycledPinSort[] }) {
  const { value, values } = props
  const navigate = useNavigate()

  return (
    <Select
      // react-aria names the trigger with the chosen order and then this, in that order, so the
      // bar carries no visible label and a reader still hears which control it is on.
      aria-label={m.sort_order()}
      value={value}
      onChange={(chosen) =>
        void navigate({ to: ".", search: (previous) => ({ ...previous, sort: recycledPinSortOr(chosen) }) })
      }
    >
      <Select.Trigger>
        <Select.Value />
        <Select.Indicator />
      </Select.Trigger>
      {/* Wider than the trigger: the popover floors at the trigger's width, where the check mark
          the selected option carries lands on the last letters of its label. */}
      <Select.Popover className="min-w-44">
        <ListBox aria-label={m.sort_order()}>
          {values.map((sort) => (
            <ListBox.Item key={sort} id={sort} textValue={LABELS[sort]()}>
              {LABELS[sort]()}
              <ListBox.ItemIndicator />
            </ListBox.Item>
          ))}
        </ListBox>
      </Select.Popover>
    </Select>
  )
}
