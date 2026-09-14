import type { Schemas } from "@pinry-reborn/auth"

/**
 * What the task centre reads of a download: which pin it feeds, and whether it still runs. The
 * status is the contract's own enumeration, which block 5 declared so that a value the server
 * never emits fails to compile here.
 */
export interface DownloadProgress {
  pinId: string
  status: Schemas["ImageDownloadOutputDto"]["status"]
}

const POLL_MS = 1000

/**
 * The list is asked again while the server still has work. A failed row also sits in the list and
 * changes only when the user retries or drops it, so it is not what polling waits for
 * (a departure from specification 4.8, which polls on the list being non-empty).
 */
export function downloadPollInterval(
  downloads: readonly DownloadProgress[] | undefined,
): number | false {
  return downloads?.some((download) => download.status === "PENDING") === true ? POLL_MS : false
}

/**
 * The pins a download that was running has just stopped feeding, either way: a success gives one
 * its image and a failure gives its tile a reason, and the grid holds neither until that pin is
 * read again. Their ids and not a flag, so what is reread is those pins and not the catalogue.
 */
export function settledPinIds(
  previous: readonly DownloadProgress[],
  current: readonly DownloadProgress[],
): string[] {
  const running = new Set(
    current.filter((download) => download.status === "PENDING").map((download) => download.pinId),
  )
  return previous
    .filter((download) => download.status === "PENDING" && !running.has(download.pinId))
    .map((download) => download.pinId)
}
