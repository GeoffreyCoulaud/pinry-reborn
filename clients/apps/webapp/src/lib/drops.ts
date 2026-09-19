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

/**
 * The drop split into what becomes pins and what is said of the rest: one reason per distinct
 * refusal, five files too heavy being one thing to say and not five (ADR 0037).
 */
export function partitionDrop(
  verdicts: readonly FileVerdict[],
  urls: readonly string[],
): DropPartition {
  const files = verdicts.filter((verdict): verdict is KeptFile => typeof verdict !== "string")
  const refused = verdicts.filter((verdict): verdict is UploadRefusal => typeof verdict === "string")
  const refusals: DropRefusal[] = [...new Set(refused)]
  // A drop that kept nothing and that nothing above speaks for is the `blob:` a browser tab hands
  // over: no file to name, and no address a server could fetch.
  if (files.length === 0 && urls.length === 0 && refusals.length === 0)
    refusals.push("UNSUPPORTED_DROP")
  return { files, urls: [...urls], refusals }
}
