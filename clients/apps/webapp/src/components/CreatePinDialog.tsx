import { Button, Input, Label, Modal, TextField } from "@heroui/react"
import { useEffect, useState } from "react"
import { refuse } from "../drops"
import { useCreatePin, useHandshake, type ImageSource } from "../images"
import { entriesOf, withDrop, type DropPartition, type PinEntry } from "../lib/drops"
import { isStorableFile, uploadRefusal } from "../lib/uploads"
import { m } from "../paraglide/messages.js"
import { ImageDropBox } from "./ImageDropBox"

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
 * One dialog and a queue of pins, worked through one entry at a time: each holds an address the
 * server fetches, a file from disk, or both, and a file is judged before a byte of it is sent.
 */
function CreatePinForm({ dropped, close }: { dropped: DropPartition | null; close: () => void }) {
  const create = useCreatePin()
  const handshake = useHandshake()
  // The queue and the index it is read at move together, so a drop judged while the user advances
  // corrects the entry the user is on now rather than the one they were on when they dropped.
  const [queue, setQueue] = useState<{ entries: readonly PinEntry[]; at: number }>(() => ({
    entries: entriesOf(dropped),
    at: 0,
  }))
  const { entries, at } = queue
  const [preview, setPreview] = useState<string | null>(null)
  // `at` is always inside the queue; the fallback is what `noUncheckedIndexedAccess` asks for.
  const entry = entries[at] ?? { file: null, url: "" }

  // The content is mounted only while the dialog is open, so this cleanup revokes the URL on
  // Escape and on the backdrop as much as on a pin created.
  useEffect(() => {
    const file = entry.file?.file
    if (file === undefined) {
      setPreview(null)
      return
    }
    const drawn = URL.createObjectURL(file)
    setPreview(drawn)
    return () => URL.revokeObjectURL(drawn)
  }, [entry.file])

  /** The entry being worked on, changed where it stands: the queue around it is untouched. */
  function change(patch: Partial<PinEntry>) {
    setQueue((current) => ({
      ...current,
      entries: current.entries.map((one, index) =>
        index === current.at ? { ...one, ...patch } : one,
      ),
    }))
  }

  /** The queue advances on the server's word, and the last entry taken closes the dialog. */
  function advance() {
    // The refusal belonged to the entry that earned it, and the next one has sent nothing yet.
    create.reset()
    if (at + 1 < entries.length) setQueue((current) => ({ ...current, at: current.at + 1 }))
    else close()
  }

  function take(drop: DropPartition) {
    setQueue((current) => ({ ...current, entries: withDrop(current.entries, current.at, drop) }))
  }

  function submit(fields: FormData) {
    // Provenance and bytes are independent: the address says where the picture was found and is
    // always sent, and it supplies the bytes only when no file was chosen.
    let source: ImageSource = { url: entry.url }
    if (entry.file !== null) {
      // Judged again here, for the file chosen before the handshake's limits arrived, the format
      // included: until they do, a picture is judged on being one rather than on being stored.
      const limits = handshake.data?.limits
      const refusal = isStorableFile(entry.file.file, limits)
        ? uploadRefusal(entry.file.measurement, limits)
        : "UNSUPPORTED_FORMAT"
      // Dropped here as it would have been at the choice, so one message means one state.
      if (refusal !== null) {
        refuse(refusal)
        change({ file: null })
        return
      }
      source = { file: entry.file.file }
    }
    create.mutate(
      {
        sourceContextUrl: String(fields.get("sourceContextUrl")) || null,
        sourceMediaUrl: entry.url || null,
        description: String(fields.get("description")),
        source,
      },
      { onSuccess: advance },
    )
  }

  return (
    <form
      // The description and the page are this entry's, and the fields holding them are the DOM's
      // own: a new key is what empties them for the next entry rather than carrying them over.
      key={at}
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault()
        submit(new FormData(event.currentTarget))
      }}
    >
      {entries.length > 1 && (
        // `role="img"` is what carries a name on something read as one unit: the two numbers are
        // for the eye and `pin_progress` is the sentence a screen reader gets.
        <span
          role="img"
          aria-label={m.pin_progress({ current: at + 1, total: entries.length })}
          className="self-end text-sm text-muted"
        >
          {at + 1}/{entries.length}
        </span>
      )}
      {/* The address stops being required once a file is chosen: an image comes from one or the other. */}
      <Field
        name="sourceMediaUrl"
        type="url"
        label={m.image_address()}
        isRequired={entry.file === null}
        value={entry.url}
        onChange={(url) => change({ url })}
      />
      <ImageDropBox limits={handshake.data?.limits} multiple onDrop={take}>
        {entry.file !== null && (
          <>
            {/* One render behind the file: the object URL is drawn by the effect above. */}
            {preview !== null && <img src={preview} alt="" className="max-h-32 rounded" />}
            <span className="text-sm text-muted">{entry.file.file.name}</span>
            {/* Positioned above the label's stretched hit area, which would otherwise take this
                press and open the picker instead. */}
            <Button variant="ghost" className="relative" onPress={() => change({ file: null })}>
              {m.remove()}
            </Button>
          </>
        )}
      </ImageDropBox>
      <Field name="description" label={m.description()} />
      {/* Never required: a file from disk and a direct image address both name no page. */}
      <Field name="sourceContextUrl" type="url" label={m.source_page()} />
      {create.isError && <p role="alert">{m.creation_refused()}</p>}
      <div className="flex justify-end gap-2">
        {/* Two verbs, two effects on the counter: `Remove` empties this entry and leaves it where
            it is, `Ignore` abandons it and moves on. */}
        {entries.length > 1 && (
          <Button variant="ghost" onPress={advance}>
            {m.ignore()}
          </Button>
        )}
        {/* Submitting before the limits arrive would send a file this deployment refuses. */}
        <Button type="submit" isDisabled={create.isPending || handshake.isPending}>
          {m.create_pin()}
        </Button>
      </div>
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
  /** What the drop that opened this dialog kept, the form opening on it. */
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
