import { Button, Input, Label, Modal, TextField } from "@heroui/react"
import { useEffect, useState } from "react"
import { useCreatePin, useHandshake, type ImageSource } from "../images"
import { isImageFile, uploadRefusal, type MeasuredUpload, type UploadRefusal } from "../lib/uploads"
import { m } from "../paraglide/messages.js"

const REFUSALS: Record<UploadRefusal, () => string> = {
  TOO_MANY_BYTES: m.file_too_heavy,
  TOO_MANY_PIXELS: m.file_too_large,
  UNSUPPORTED_FORMAT: m.file_unsupported,
  UNREADABLE: m.file_unreadable,
}

/** The pixel count a limit is read against, which nothing short of a decoder knows. */
async function measured(file: File): Promise<MeasuredUpload> {
  const bitmap = await createImageBitmap(file)
  return { size: file.size, width: bitmap.width, height: bitmap.height }
}

/** A file this deployment accepts, with the object URL its thumbnail is drawn from. */
interface Chosen {
  file: File
  measurement: MeasuredUpload
  preview: string
}

/**
 * The label follows the input so Tailwind's `peer-*` variants reach it. react-aria links the two
 * by identifier rather than by order, so the accessible name is unchanged by the move.
 */
function Field({
  name,
  label,
  type,
  isRequired,
}: {
  name: string
  label: string
  type?: "text" | "url"
  isRequired?: boolean
}) {
  return (
    <TextField name={name} type={type} isRequired={isRequired} className="relative">
      <Input placeholder=" " className="pt-6 pb-1 peer" />
      <Label className="pointer-events-none absolute start-3 top-1 text-xs transition-all peer-placeholder-shown:top-3.5 peer-placeholder-shown:text-base motion-reduce:transition-none">
        {label}
      </Label>
    </TextField>
  )
}

/**
 * Two entries, one dialog: an address the server fetches, or a file from disk. The file is judged
 * the moment it is chosen, so a refused one costs no upload and says so at once (decision M).
 */
function CreatePinForm({ close }: { close: () => void }) {
  const create = useCreatePin()
  const handshake = useHandshake()
  const [chosen, setChosen] = useState<Chosen | null>(null)
  const [refused, setRefused] = useState<UploadRefusal | null>(null)

  // The content is mounted only while the dialog is open, so this cleanup is what revokes the
  // URL, on Escape and on the backdrop as much as on a pin created.
  useEffect(() => {
    const preview = chosen?.preview
    return () => {
      if (preview !== undefined) URL.revokeObjectURL(preview)
    }
  }, [chosen])

  async function choose(files: FileList | null) {
    const candidates = [...(files ?? [])]
    // A drop may carry several files: the first image wins, and one carrying none is refused.
    const file = candidates.find(isImageFile) ?? candidates[0]
    setChosen(null)
    if (file === undefined) {
      setRefused(null)
      return
    }
    if (!isImageFile(file)) {
      setRefused("UNSUPPORTED_FORMAT")
      return
    }
    let measurement: MeasuredUpload
    try {
      measurement = await measured(file)
    } catch {
      setRefused("UNREADABLE")
      return
    }
    const refusal = uploadRefusal(measurement, handshake.data?.limits)
    setRefused(refusal)
    if (refusal === null) setChosen({ file, measurement, preview: URL.createObjectURL(file) })
  }

  function submit(fields: FormData) {
    let source: ImageSource = { url: String(fields.get("sourceMediaUrl")) }
    if (chosen !== null) {
      // Judged again here, for the file chosen before the handshake's limits arrived.
      const refusal = uploadRefusal(chosen.measurement, handshake.data?.limits)
      setRefused(refusal)
      if (refusal !== null) return
      source = { file: chosen.file }
    }
    create.mutate(
      {
        sourceContextUrl: String(fields.get("sourceContextUrl")) || null,
        description: String(fields.get("description")),
        source,
      },
      { onSuccess: close },
    )
  }

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault()
        submit(new FormData(event.currentTarget))
      }}
    >
      {/* The address stops being required once a file is chosen: an image comes from one or the other. */}
      <Field
        name="sourceMediaUrl"
        type="url"
        label={m.image_address()}
        isRequired={chosen === null}
      />
      <div
        className="relative flex flex-col items-center gap-2 rounded-lg border border-dashed border-separator p-4 text-center"
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault()
          void choose(event.dataTransfer.files)
        }}
      >
        {/* The invitation is the input's accessible name, which is what Label in Name asks for.
            Its hit area is stretched over the whole box, so the thumbnail and the padding are
            clickable too without joining the name. */}
        <label className="cursor-pointer before:absolute before:inset-0 before:content-['']">
          {m.drop_image()}
          <input
            name="file"
            type="file"
            accept="image/*"
            className="sr-only"
            onChange={(event) => void choose(event.currentTarget.files)}
          />
        </label>
        {chosen !== null && (
          <>
            <img src={chosen.preview} alt="" className="max-h-32 rounded" />
            <span className="text-sm text-muted">{chosen.file.name}</span>
          </>
        )}
      </div>
      {refused !== null && <p role="alert">{REFUSALS[refused]()}</p>}
      <Field name="description" label={m.description()} />
      {/* Never required: a file from disk and a direct image address both name no page. */}
      <Field name="sourceContextUrl" type="url" label={m.source_page()} />
      {create.isError && <p role="alert">{m.creation_refused()}</p>}
      {/* Submitting before the limits arrive would send a file this deployment refuses. */}
      <Button type="submit" isDisabled={create.isPending || handshake.isPending}>
        {m.create_pin()}
      </Button>
    </form>
  )
}

export function CreatePinDialog({
  isOpen,
  onOpenChange,
}: {
  isOpen: boolean
  onOpenChange: (open: boolean) => void
}) {
  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable>
      <Modal.Container size="sm">
        <Modal.Dialog aria-label={m.create_pin()}>
          {isOpen && <CreatePinForm close={() => onOpenChange(false)} />}
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  )
}
