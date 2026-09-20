import type { Schemas } from "@pinry-reborn/auth"

/** Why this deployment will not store this file, told before a byte of it is sent. */
export type UploadRefusal =
  | "TOO_MANY_BYTES"
  | "TOO_MANY_PIXELS"
  | "UNSUPPORTED_FORMAT"
  | "UNREADABLE"

/** The limits the handshake publishes, read from the contract rather than retyped (4.3). */
export type UploadLimits = Schemas["HandshakeOutputDto"]["limits"]

/** A file the browser has decoded far enough to know what it would cost the server. */
export interface MeasuredUpload {
  size: number
  width: number
  height: number
}

/**
 * The limits are the deployment's, so they are read from the handshake rather than held here: a
 * bound written into the bundle drifts from the instance that configures it (specification
 * 2026-09-10, 4.3). Unknown limits refuse nothing, and the upload then meets the server's own
 * answer.
 */
export function uploadRefusal(
  upload: MeasuredUpload,
  limits: UploadLimits | undefined,
): UploadRefusal | null {
  if (limits === undefined) return null
  if (upload.size > limits.maxFileBytes) return "TOO_MANY_BYTES"
  if (upload.width * upload.height > limits.maxPixels) return "TOO_MANY_PIXELS"
  return null
}

/**
 * A drop bypasses `accept`, which only the file picker honours, so the type is read here too. The
 * formats are the deployment's, as the limits are: an SVG is a picture the browser decodes and the
 * storage refuses. Before the handshake answers, only what the browser calls a picture passes, and
 * the format then meets the server's own answer.
 */
export function isStorableFile(file: { type: string }, limits: UploadLimits | undefined): boolean {
  if (limits === undefined) return file.type.startsWith("image/")
  return limits.mediaTypes.includes(file.type)
}
