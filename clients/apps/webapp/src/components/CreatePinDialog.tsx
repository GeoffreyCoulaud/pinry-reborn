import { Button, Input, Label, Modal, TextField } from "@heroui/react"
import { useEffect, useState } from "react"
import { judgeDrop, refuse } from "../drops"
import { useCreatePin, useHandshake, type ImageSource } from "../images"
import { dragDepth, type DragStep } from "../lib/drags"
import type { DropPartition, KeptFile } from "../lib/drops"
import { uploadRefusal } from "../lib/uploads"
import { m } from "../paraglide/messages.js"

/** A file this deployment accepts, with the object URL its thumbnail is drawn from. */
type Chosen = KeptFile & { preview: string }

/** The thumbnail a kept file is shown by, drawn from bytes the browser already holds. */
function shown(kept: KeptFile | undefined): Chosen | null {
  if (kept === undefined) return null
  return { ...kept, preview: URL.createObjectURL(kept.file) }
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
  value,
  onChange,
}: {
  name: string
  label: string
  type?: "text" | "url"
  isRequired?: boolean
  value?: string
  onChange?: (value: string) => void
}) {
  return (
    <TextField
      name={name}
      type={type}
      isRequired={isRequired}
      value={value}
      onChange={onChange}
      className="relative"
    >
      <Input placeholder=" " className="pt-6 pb-2 peer" />
      {/* The label rests centred and floats on focus as well as on content, so the caret never
          shares its line. Both positions are centred by hand against the field's height. */}
      <Label className="pointer-events-none absolute start-3 top-4 text-base font-normal transition-all peer-focus:top-2 peer-focus:text-xs peer-not-placeholder-shown:top-2 peer-not-placeholder-shown:text-xs motion-reduce:transition-none">
        {label}
      </Label>
    </TextField>
  )
}

/**
 * Two entries, one dialog: an address the server fetches, or a file from disk. The file is judged
 * the moment it is chosen, so a refused one costs no upload and says so at once (decision M).
 */
function CreatePinForm({ dropped, close }: { dropped: DropPartition | null; close: () => void }) {
  const create = useCreatePin()
  const handshake = useHandshake()
  // The drop that opened this form was judged in full before it did (decision N), so the first
  // entry it kept is what the form opens on.
  const [chosen, setChosen] = useState<Chosen | null>(() => shown(dropped?.files[0]))
  const [foundAt, setFoundAt] = useState(dropped?.urls[0] ?? "")
  const [depth, setDepth] = useState(0)

  function dragged(step: DragStep) {
    setDepth((current) => dragDepth(current, step))
  }

  // The content is mounted only while the dialog is open, so this cleanup is what revokes the
  // URL, on Escape and on the backdrop as much as on a pin created.
  useEffect(() => {
    const preview = chosen?.preview
    return () => {
      if (preview !== undefined) URL.revokeObjectURL(preview)
    }
  }, [chosen])

  /**
   * One element dropped replaces the file this entry holds, dropping on an open form being the
   * correction gesture; an address touches provenance alone and takes nothing away (decision H).
   */
  async function take(files: readonly File[], uriList: string) {
    const drop = await judgeDrop(files, uriList, handshake.data?.limits)
    drop.refusals.forEach(refuse)
    if (drop.files[0] !== undefined) setChosen(shown(drop.files[0]))
    if (drop.urls[0] !== undefined) setFoundAt(drop.urls[0])
  }

  function submit(fields: FormData) {
    // Provenance and bytes are independent: the address says where the picture was found and is
    // always sent, and it supplies the bytes only when no file was chosen (decision G).
    let source: ImageSource = { url: foundAt }
    if (chosen !== null) {
      // Judged again here, for the file chosen before the handshake's limits arrived.
      const refusal = uploadRefusal(chosen.measurement, handshake.data?.limits)
      // Dropped here as it would have been at the choice, so one message means one state.
      if (refusal !== null) {
        refuse(refusal)
        setChosen(null)
        return
      }
      source = { file: chosen.file }
    }
    create.mutate(
      {
        sourceContextUrl: String(fields.get("sourceContextUrl")) || null,
        sourceMediaUrl: foundAt || null,
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
        value={foundAt}
        onChange={setFoundAt}
      />
      {/* `data-dragging` carries the counter rather than a class, jsdom computing no style: it is
          what the active style hangs on and the only thing a test can read. */}
      <div
        className="relative flex flex-col items-center gap-2 rounded-lg border border-dashed border-separator p-4 text-center has-[input:focus-visible]:ring-2 has-[input:focus-visible]:ring-focus data-dragging:border-accent data-dragging:bg-accent-soft"
        data-dragging={depth > 0 ? "" : undefined}
        onDragEnter={() => dragged("enter")}
        onDragLeave={() => dragged("leave")}
        // Without this the browser fires no `drop` at all, whatever the handler below says.
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault()
          dragged("drop")
          void take([...event.dataTransfer.files], event.dataTransfer.getData("text/uri-list"))
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
            onChange={(event) => {
              const input = event.currentTarget
              // What is chosen with the mouse is judged where what is dropped is (decision L).
              void take([...(input.files ?? [])], "")
              // An input still holding a file fires no change event for that same file, so the
              // one just removed could never be chosen again.
              input.value = ""
            }}
          />
        </label>
        {chosen !== null && (
          <>
            <img src={chosen.preview} alt="" className="max-h-32 rounded" />
            <span className="text-sm text-muted">{chosen.file.name}</span>
            {/* Positioned above the label's stretched hit area, which would otherwise take this
                press and open the picker instead. */}
            <Button variant="ghost" className="relative" onPress={() => setChosen(null)}>
              {m.remove()}
            </Button>
          </>
        )}
      </div>
      <Field name="description" label={m.description()} />
      {/* Never required: a file from disk and a direct image address both name no page. */}
      <Field name="sourceContextUrl" type="url" label={m.source_page()} />
      {create.isError && <p role="alert">{m.creation_refused()}</p>}
      {/* Submitting before the limits arrive would send a file this deployment refuses. */}
      <Button type="submit" className="self-end" isDisabled={create.isPending || handshake.isPending}>
        {m.create_pin()}
      </Button>
    </form>
  )
}

export function CreatePinDialog({
  isOpen,
  onOpenChange,
  dropped,
}: {
  isOpen: boolean
  onOpenChange: (open: boolean) => void
  /** What the drop that opened this dialog kept, the form opening on it (decision D). */
  dropped: DropPartition | null
}) {
  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable>
      <Modal.Container size="sm">
        <Modal.Dialog>
          {/* HeroUI hardcodes `aria-label="Close"` and spreads `...rest` after it, so the
              catalogue's string wins. It places itself top right and closes through the dialog's
              own `slot="close"`. */}
          <Modal.CloseTrigger aria-label={m.close()} />
          {/* `slot="title"` is what names the dialog, so the visible name and the read one are one. */}
          <Modal.Heading level={2} className="mb-3 pe-8">
            {m.create_pin()}
          </Modal.Heading>
          {isOpen && <CreatePinForm dropped={dropped} close={() => onOpenChange(false)} />}
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  )
}
