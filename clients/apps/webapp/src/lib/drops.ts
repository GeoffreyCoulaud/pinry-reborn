import type { MeasuredUpload, UploadRefusal } from "./uploads"

/** Why a drop, or something it carried, could not become a pin. */
export type DropRefusal = UploadRefusal | "UNSUPPORTED_DROP"

/** A file kept: its bytes, and the measurement the re-judge at submission reads back. */
export interface KeptFile {
  file: File
  measurement: MeasuredUpload
}

/** One file's verdict: the file itself when it is kept, the reason it is not otherwise. */
export type FileVerdict = KeptFile | UploadRefusal

/** What a drop carried, once every file in it has been judged. */
export interface DropPartition {
  files: KeptFile[]
  urls: string[]
  refusals: DropRefusal[]
}

/** One pin being composed: the bytes, the address the picture was found at, or both. */
export interface PinEntry {
  file: KeptFile | null
  url: string
}

/**
 * The entries a drop becomes, a file and the address beside it being one pin and not two. Always at
 * least one, a form opened by hand holding a blank entry.
 */
export function entriesOf(drop: DropPartition | null): PinEntry[] {
  const count = Math.max(drop?.files.length ?? 0, drop?.urls.length ?? 0, 1)
  return Array.from({ length: count }, (_, at) => ({
    file: drop?.files[at] ?? null,
    url: drop?.urls[at] ?? "",
  }))
}

/** One element arriving corrects the entry being worked on, dropping on a form being that gesture. */
function corrected(entry: PinEntry, arriving: readonly PinEntry[]): PinEntry {
  return arriving.reduce(
    // A file arriving with no address of its own takes the old one away with it: the address says
    // where *this* picture was found, and the picture has just been replaced.
    (into, one) => ({
      file: one.file ?? into.file,
      url: one.url || (one.file === null ? into.url : ""),
    }),
    entry,
  )
}

/**
 * The queue after a drop landed on the open form: one element corrects the entry being worked on,
 * several are "add these three" and go to the end of a total the user has already read.
 */
export function withDrop(
  entries: readonly PinEntry[],
  current: number,
  drop: DropPartition,
): PinEntry[] {
  const arriving = entriesOf(drop)
  if (arriving.length > 1) return [...entries, ...arriving]
  return entries.map((entry, at) => (at === current ? corrected(entry, arriving) : entry))
}

/**
 * The drop split into what becomes pins and what is said of the rest: one reason per distinct
 * refusal, five files too heavy being one thing to say and not five.
 */
export function partitionDrop(
  verdicts: readonly FileVerdict[],
  urls: readonly string[],
): DropPartition {
  const files = verdicts.filter((verdict): verdict is KeptFile => typeof verdict !== "string")
  const refused = verdicts.filter((verdict): verdict is UploadRefusal => typeof verdict === "string")
  const refusals: DropRefusal[] = [...new Set(refused)]
  // An address stands at its own file's index, so a refusal takes the pair: compacting the files
  // alone would pair every file behind it with the address of the one refused.
  const kept = urls.filter((_, at) => typeof verdicts[at] !== "string")
  // A drop that kept nothing and that nothing above speaks for is the `blob:` a browser tab hands
  // over: no file to name, and no address a server could fetch.
  if (files.length === 0 && kept.length === 0 && refusals.length === 0)
    refusals.push("UNSUPPORTED_DROP")
  return { files, urls: kept, refusals }
}
