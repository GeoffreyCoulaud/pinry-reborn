import { Button, Input, Label, ListBox, Select, Tag, TagGroup, TextArea, TextField } from "@heroui/react"
import { useEffect, useState } from "react"
import { useBoards } from "../boards"
import { downloadReason } from "../downloadReasons"
import { judgeDrop, refuse } from "../drops"
import { useHandshake, useSetPinImage } from "../images"
import { dragDepth } from "../lib/drags"
import type { KeptFile } from "../lib/drops"
import { tileImageSource } from "../lib/tiles"
import type { UploadLimits } from "../lib/uploads"
import { m } from "../paraglide/messages.js"
import { useTagSearch, useUpdatePin, type Pin } from "../pins"

function Field({
  name,
  label,
  type,
  defaultValue,
  value,
  onChange,
}: {
  name?: string
  label: string
  type?: "text" | "url"
  defaultValue?: string
  value?: string
  onChange?: (value: string) => void
}) {
  return (
    <TextField
      name={name}
      type={type}
      defaultValue={defaultValue}
      value={value}
      onChange={onChange}
    >
      <Label>{label}</Label>
      <Input />
    </TextField>
  )
}

/**
 * Free text over the author's own names. The server decides which names are one tag, folding to
 * ASCII, so the field asks it on every keystroke and offers what it answers rather than deciding
 * it is looking at a new tag (decision L).
 */
function TagField({
  names,
  onChange,
}: {
  names: readonly string[]
  onChange: (names: readonly string[]) => void
}) {
  const [typed, setTyped] = useState("")
  const offered = (useTagSearch(typed).data ?? []).filter((name) => !names.includes(name))

  function add(name: string) {
    const held = name.trim()
    if (held !== "" && !names.includes(held)) onChange([...names, held])
    setTyped("")
  }

  return (
    <div className="flex flex-col gap-2">
      <TextField value={typed} onChange={setTyped}>
        <Label>{m.tags()}</Label>
        <Input
          onKeyDown={(event) => {
            // The field sits inside the pin's form, where Enter would save it: here it names a tag.
            if (event.key !== "Enter") return
            event.preventDefault()
            add(typed)
          }}
        />
      </TextField>
      {names.length > 0 && (
        <TagGroup
          aria-label={m.tags_chosen()}
          onRemove={(keys) => onChange(names.filter((name) => !keys.has(name)))}
        >
          <TagGroup.List items={names.map((name) => ({ id: name }))}>
            {(tag) => (
              <Tag>
                {String(tag.id)}
                <Tag.RemoveButton />
              </Tag>
            )}
          </TagGroup.List>
        </TagGroup>
      )}
      {/* Last, and outlined: a suggestion appears and goes as the user types, so it moves nothing
          above it, and a chip that reads as flat text is one nobody presses. */}
      {offered.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {offered.map((name) => (
            <li key={name}>
              <Button size="sm" variant="outline" onPress={() => add(name)}>
                {name}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** The boards the pin is filed under. The list arrives whole, so there is no page to chase. */
function BoardField({
  ids,
  onChange,
}: {
  ids: readonly string[]
  onChange: (ids: readonly string[]) => void
}) {
  const boards = useBoards()

  return (
    <Select
      selectionMode="multiple"
      placeholder={m.boards_none()}
      value={ids}
      onChange={(chosen) => onChange(chosen.map(String))}
    >
      <Label>{m.boards()}</Label>
      <Select.Trigger>
        <Select.Value />
        <Select.Indicator />
      </Select.Trigger>
      <Select.Popover className="min-w-44">
        <ListBox aria-label={m.boards()} items={boards.data ?? []}>
          {(held) => (
            <ListBox.Item id={held.id} textValue={held.name}>
              {held.name}
              <ListBox.ItemIndicator />
            </ListBox.Item>
          )}
        </ListBox>
      </Select.Popover>
    </Select>
  )
}

/** What the form is editing: the picture the pin carries, or the file about to replace it. */
function ImagePreview({ pin, chosen }: { pin: Pin; chosen: KeptFile | null }) {
  const [preview, setPreview] = useState<string | null>(null)

  // The form is mounted only while the dialog is editing, so this revokes the URL on Cancel and
  // on the pin saved as much as on another file chosen.
  useEffect(() => {
    if (chosen === null) {
      setPreview(null)
      return
    }
    const drawn = URL.createObjectURL(chosen.file)
    setPreview(drawn)
    return () => URL.revokeObjectURL(drawn)
  }, [chosen])

  const held = pin.image?.url
  const source = preview ?? (held != null ? tileImageSource(held, "MEDIUM") : null)
  if (source === null) return null
  return (
    <img
      src={source}
      // The pin's own picture is what its description names; a file not sent yet is not the pin.
      alt={preview === null ? pin.description : ""}
      className="max-h-48 w-full rounded object-contain"
    />
  )
}

/**
 * The file that replaces the image, chosen the way the creation screen has one chosen: the same
 * box, the same judging before a byte is sent, and the address a drop carried taken as well.
 */
function ReplaceField({
  chosen,
  limits,
  onFile,
  onAddress,
}: {
  chosen: KeptFile | null
  limits: UploadLimits | undefined
  onFile: (file: KeptFile | null) => void
  onAddress: (url: string) => void
}) {
  const [depth, setDepth] = useState(0)

  async function take(files: readonly File[], uriList: string) {
    const judged = await judgeDrop(files, uriList, limits)
    judged.refusals.forEach(refuse)
    if (judged.files[0] !== undefined) onFile(judged.files[0])
    if (judged.urls[0] !== undefined) onAddress(judged.urls[0])
  }

  return (
    // `data-dragging` carries the counter rather than a class, jsdom computing no style.
    <div
      className="relative flex flex-col items-center gap-2 rounded-lg border border-dashed border-separator p-4 text-center has-[input:focus-visible]:ring-2 has-[input:focus-visible]:ring-focus data-dragging:border-accent data-dragging:bg-accent-soft"
      data-dragging={depth > 0 ? "" : undefined}
      onDragEnter={() => setDepth((current) => dragDepth(current, "enter"))}
      onDragLeave={() => setDepth((current) => dragDepth(current, "leave"))}
      // Without this the browser fires no `drop` at all, whatever the handler below says.
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => {
        event.preventDefault()
        setDepth((current) => dragDepth(current, "drop"))
        void take([...event.dataTransfer.files], event.dataTransfer.getData("text/uri-list"))
      }}
    >
      <label className="cursor-pointer before:absolute before:inset-0 before:content-['']">
        {m.drop_image()}
        <input
          type="file"
          accept={limits?.mediaTypes.join(",") ?? "image/*"}
          className="sr-only"
          onChange={(event) => {
            const input = event.currentTarget
            void take([...(input.files ?? [])], "")
            // An input still holding a file fires no change event for that same file.
            input.value = ""
          }}
        />
      </label>
      {chosen !== null && (
        <>
          <span className="text-sm text-muted">{chosen.file.name}</span>
          {/* Above the label's stretched hit area, which would otherwise open the picker. */}
          <Button variant="ghost" className="relative" onPress={() => onFile(null)}>
            {m.remove()}
          </Button>
        </>
      )}
    </div>
  )
}

/**
 * What edits a pin is a set of fields, where what shows one is an image and its words (decision K).
 * Every field is sent on every save: the route replaces the pin, and an empty list clears.
 */
export function PinEditForm({ pin, close }: { pin: Pin; close: () => void }) {
  const save = useUpdatePin()
  const setImage = useSetPinImage()
  const limits = useHandshake().data?.limits
  const [tags, setTags] = useState<readonly string[]>(pin.tags.map((tag) => tag.name))
  const [boardIds, setBoardIds] = useState<readonly string[]>(pin.boards.map((board) => board.id))
  // The address is the form's, not the pin's: the fetch below reads what is typed now, so an
  // address added during this edit is fetchable without saving and reopening the dialog.
  const [address, setAddress] = useState(pin.sourceMediaUrl ?? "")
  const [chosen, setChosen] = useState<KeptFile | null>(null)
  const replacement = pin.image?.replacement

  return (
    <div className="flex flex-col gap-3">
      <ImagePreview pin={pin} chosen={chosen} />
      {/* The sub-state the contract has carried since before any client read it: the pin keeps the
          image it has while the server downloads the one asked for. */}
      {replacement?.status === "PENDING" && <p role="status">{m.image_replacing()}</p>}
      {replacement?.status === "FAILED" && (
        <p role="alert">{downloadReason(replacement.reasonCode, replacement.message)}</p>
      )}
      {setImage.isError && <p role="alert">{m.image_refused()}</p>}
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          const fields = new FormData(event.currentTarget)
          save.mutate(
            {
              pinId: pin.id,
              body: {
                description: String(fields.get("description")),
                // An address emptied is an address cleared, the replacement being total.
                sourceContextUrl: String(fields.get("sourceContextUrl")) || null,
                sourceMediaUrl: address || null,
                tags: [...tags],
                boardIds: [...boardIds],
              },
            },
            {
              // The pin first, then its image, as the creation screen writes the two.
              onSuccess: () => {
                if (chosen === null) close()
                else
                  setImage.mutate({ pinId: pin.id, source: { file: chosen.file } }, { onSuccess: close })
              },
            },
          )
        }}
      >
        <Field type="url" label={m.image_address()} value={address} onChange={setAddress} />
        {/* On the field's value and not on the pin's: an address just typed is one to fetch from. */}
        {address.trim() !== "" && (
          <Button
            variant="secondary"
            className="self-start"
            isDisabled={setImage.isPending}
            onPress={() => setImage.mutate({ pinId: pin.id, source: { url: address } })}
          >
            {m.fetch_image_from_url()}
          </Button>
        )}
        <ReplaceField
          chosen={chosen}
          limits={limits}
          onFile={setChosen}
          onAddress={setAddress}
        />
        <TextField name="description" defaultValue={pin.description}>
          <Label>{m.description()}</Label>
          <TextArea rows={3} />
        </TextField>
        <Field
          name="sourceContextUrl"
          type="url"
          label={m.source_page()}
          defaultValue={pin.sourceContextUrl ?? ""}
        />
        <TagField names={tags} onChange={setTags} />
        <BoardField ids={boardIds} onChange={setBoardIds} />
        {save.isError && <p role="alert">{m.pin_refused()}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onPress={close}>
            {m.cancel()}
          </Button>
          <Button type="submit" isDisabled={save.isPending || setImage.isPending}>
            {m.save()}
          </Button>
        </div>
      </form>
    </div>
  )
}
