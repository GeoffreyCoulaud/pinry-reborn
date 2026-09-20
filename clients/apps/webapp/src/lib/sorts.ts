import type { Schemas } from "@pinry-reborn/auth"

export type PinSort = Schemas["PinSortStrategyInputEnum"]

/** The grid's orders and the one the bin adds to them (specification 2026-09-20, 2.5). */
export type RecycledPinSort = Schemas["PinRecycleBinSortStrategyInputEnum"]

/** Newest first leads, being the order the grid defaults to and not the one the API does. */
export const PIN_SORTS: readonly PinSort[] = ["CREATED_AT_DESC", "CREATED_AT_ASC"]

/** Just deleted leads: the bin is read from what has only now left the grid. */
export const RECYCLED_PIN_SORTS: readonly RecycledPinSort[] = ["DELETED_AT_DESC", ...PIN_SORTS]

/**
 * A search parameter is whatever the address bar holds, so a value the API would refuse falls
 * back to the default rather than reaching it.
 */
export function pinSortOr(value: unknown): PinSort {
  return PIN_SORTS.find((sort) => sort === value) ?? "CREATED_AT_DESC"
}

export function recycledPinSortOr(value: unknown): RecycledPinSort {
  return RECYCLED_PIN_SORTS.find((sort) => sort === value) ?? "DELETED_AT_DESC"
}
