import { toast } from "@heroui/react"
import { partitionDrop, type DropPartition, type DropRefusal, type FileVerdict } from "./lib/drops"
import { isStorableFile, uploadRefusal, type MeasuredUpload, type UploadLimits } from "./lib/uploads"
import { urisFromDrop } from "./lib/uris"
import { m } from "./paraglide/messages.js"

const REFUSALS: Record<DropRefusal, () => string> = {
  TOO_MANY_BYTES: m.file_too_heavy,
  TOO_MANY_PIXELS: m.file_too_large,
  UNSUPPORTED_FORMAT: m.file_unsupported,
  UNREADABLE: m.file_unreadable,
  UNSUPPORTED_DROP: m.drop_unsupported,
}

/** An element refused speaks where the gesture happened, and the gesture owns no form. */
export function refuse(refusal: DropRefusal) {
  toast.danger(REFUSALS[refusal]())
}

/** The pixel count a limit is read against, which nothing short of a decoder knows. */
async function measured(file: File): Promise<MeasuredUpload> {
  const bitmap = await createImageBitmap(file)
  const measurement = { size: file.size, width: bitmap.width, height: bitmap.height }
  // A decoded bitmap is four bytes a pixel, so ten photographs held at once are hundreds of
  // megabytes (MDN, `ImageBitmap.close()`).
  bitmap.close()
  return measurement
}

/** One file, judged before a byte of it is sent: the file kept, or the reason it is not. */
async function judge(file: File, limits: UploadLimits | undefined): Promise<FileVerdict> {
  // A drop bypasses `accept`, which only the file picker honours, so a format refused costs no
  // decode at all.
  if (!isStorableFile(file, limits)) return "UNSUPPORTED_FORMAT"
  let measurement: MeasuredUpload
  try {
    measurement = await measured(file)
  } catch {
    return "UNREADABLE"
  }
  return uploadRefusal(measurement, limits) ?? { file, measurement }
}

/**
 * What a gesture hands over, judged in full before anything of it is shown. The files are measured
 * in series so one decoded bitmap is held at a time, and the file picker enters here too.
 */
export async function judgeDrop(
  files: readonly File[],
  uriList: string,
  limits: UploadLimits | undefined,
): Promise<DropPartition> {
  const verdicts: FileVerdict[] = []
  for (const file of files) verdicts.push(await judge(file, limits))
  return partitionDrop(verdicts, urisFromDrop(uriList))
}
