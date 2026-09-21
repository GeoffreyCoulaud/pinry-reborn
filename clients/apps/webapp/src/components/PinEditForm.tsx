import {
  Button,
  Input,
  Label,
  ListBox,
  Radio,
  RadioGroup,
  Select,
  Tag,
  TagGroup,
  TextArea,
  TextField,
} from "@heroui/react"
import { useEffect, useState } from "react"
import { useBoards } from "../boards"
import { useDebounced } from "../debounce"
import { downloadReason } from "../downloadReasons"
import { useHandshake, useSetPinImage, type ImageSource } from "../images"
import type { KeptFile } from "../lib/drops"
import { tileImageSource } from "../lib/tiles"
import { m } from "../paraglide/messages.js"
import { useTagSearch, useUpdatePin, type Pin } from "../pins"
import { ImageDropBox } from "./ImageDropBox"

/**
 * Free text over the author's own names. The server decides which names are one tag, folding to
 * ASCII, so the field asks it and offers what it answers rather than deciding it is looking at a
 * new tag (specification 2026-09-20, decision L). It asks once the typing pauses, not once per
 * character (specification 2026-09-21, decision P).
 */
function TagField({
  names,
  onChange,
}: {
  names: readonly string[]
  onChange: (names: readonly string[]) => void
}) {
  const [typed, setTyped] = useState("")
  const asked = useDebounced(typed)
  const offered = (useTagSearch(asked).data ?? []).filter((name) => !names.includes(name))

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

/** What the save does to the image, one of three and applied after the pin is written. */
type ImageIntent = "keep" | "replace" | "fetch"

function ImageOption({ value, label }: { value: ImageIntent; label: string }) {
  return (
    <Radio value={value}>
      <Radio.Content>
        <Radio.Control>
          <Radio.Indicator />
        </Radio.Control>
        {label}
      </Radio.Content>
    </Radio>
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
 * What edits a pin is a set of fields, where what shows one is an image and its words (decision K).
 * Every field is sent on every save: the route replaces the pin, and an empty list clears.
 */
export function PinEditForm({ pin, close }: { pin: Pin; close: () => void }) {
  const save = useUpdatePin()
  const setImage = useSetPinImage()
  const limits = useHandshake().data?.limits
  const [tags, setTags] = useState<readonly string[]>(pin.tags.map((tag) => tag.name))
  const [boardIds, setBoardIds] = useState<readonly string[]>(pin.boards.map((board) => board.id))
  // The address is the form's, not the pin's: the fetch is offered on what the field holds now,
  // so an address added during this edit needs no save and no second edit to be fetched from.
  const [address, setAddress] = useState(pin.sourceMediaUrl ?? "")
  const [intent, setIntent] = useState<ImageIntent>("keep")
  const [chosen, setChosen] = useState<KeptFile | null>(null)
  const replacement = pin.image?.replacement
  const fetchable = address.trim() !== ""

  /** What the save applies to the image once the pin itself is written, or nothing. */
  function source(): ImageSource | null {
    if (intent === "replace" && chosen !== null) return { file: chosen.file }
    if (intent === "fetch") return { url: address }
    return null
  }

  function changeAddress(typed: string) {
    setAddress(typed)
    // The fetch reads that field, so an address taken away takes the option with it.
    if (typed.trim() === "" && intent === "fetch") setIntent("keep")
  }

  return (
    <div className="flex flex-col gap-3">
      <ImagePreview pin={pin} chosen={intent === "replace" ? chosen : null} />
      {/* The sub-state the contract has carried since before any client read it: the pin keeps the
          image it has while the server downloads the one asked for. */}
      {replacement?.status === "PENDING" && <p role="status">{m.image_replacing()}</p>}
      {replacement?.status === "FAILED" && (
        <p role="alert">{downloadReason(replacement.reasonCode, replacement.message)}</p>
      )}
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
              // The pin first, then the option chosen: a fetch then reads the address the pin
              // holds rather than one the server has never seen. A refused pin touches no image.
              onSuccess: () => {
                const chosenSource = source()
                if (chosenSource === null) close()
                else setImage.mutate({ pinId: pin.id, source: chosenSource }, { onSuccess: close })
              },
            },
          )
        }}
      >
        <TextField type="url" value={address} onChange={changeAddress}>
          <Label>{m.image_address()}</Label>
          <Input />
        </TextField>
        <RadioGroup value={intent} onChange={(value) => setIntent(value as ImageIntent)}>
          <Label>{m.image()}</Label>
          <ImageOption value="keep" label={m.image_keep()} />
          <ImageOption value="replace" label={m.image_replace()} />
          {/* On the field's value and not on the pin's: an address just typed is one to fetch from. */}
          {fetchable && <ImageOption value="fetch" label={m.fetch_image_from_url()} />}
        </RadioGroup>
        {/* The file replacing the image is chosen the way the creation screen has one chosen, and
            an address the same drop carried is taken as well. */}
        {intent === "replace" && (
          <ImageDropBox
            limits={limits}
            onDrop={(drop) => {
              if (drop.files[0] !== undefined) setChosen(drop.files[0])
              if (drop.urls[0] !== undefined) changeAddress(drop.urls[0])
            }}
          >
            {chosen !== null && (
              <>
                <span className="text-sm text-muted">{chosen.file.name}</span>
                {/* Above the label's stretched hit area, which would otherwise open the picker. */}
                <Button variant="ghost" className="relative" onPress={() => setChosen(null)}>
                  {m.remove()}
                </Button>
              </>
            )}
          </ImageDropBox>
        )}
        <TextField name="description" defaultValue={pin.description}>
          <Label>{m.description()}</Label>
          <TextArea rows={3} />
        </TextField>
        <TextField name="sourceContextUrl" type="url" defaultValue={pin.sourceContextUrl ?? ""}>
          <Label>{m.source_page()}</Label>
          <Input />
        </TextField>
        <TagField names={tags} onChange={setTags} />
        <BoardField ids={boardIds} onChange={setBoardIds} />
        {/* Two halves, two sentences: the pin is written before its image, so a refused image
            leaves the fields saved and only the image to try again. */}
        {save.isError && <p role="alert">{m.pin_refused()}</p>}
        {setImage.isError && <p role="alert">{m.image_refused()}</p>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onPress={close}>
            {m.cancel()}
          </Button>
          <Button
            type="submit"
            // Replace with nothing chosen would save as though the image were being kept.
            isDisabled={save.isPending || setImage.isPending || (intent === "replace" && chosen === null)}
          >
            {m.save()}
          </Button>
        </div>
      </form>
    </div>
  )
}
