import type { Schemas } from "@pinry-reborn/auth"

export type PinSort = Schemas["PinSortStrategyInputEnum"]

/** Newest first leads, being the order the grid defaults to and not the one the API does. */
export const PIN_SORTS: readonly PinSort[] = ["CREATED_AT_DESC", "CREATED_AT_ASC"]

/**
 * A search parameter is whatever the address bar holds, so a value the API would refuse falls
 * back to the default rather than reaching it.
 */
export function pinSortOr(value: unknown): PinSort {
  return PIN_SORTS.find((sort) => sort === value) ?? "CREATED_AT_DESC"
}
