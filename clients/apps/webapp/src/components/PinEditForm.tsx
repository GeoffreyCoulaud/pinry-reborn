import {
  Button,
  Input,
  Label,
  ListBox,
  Select,
  Tag,
  TagGroup,
  TextArea,
  TextField,
  buttonVariants,
} from "@heroui/react"
import { useState } from "react"
import { useBoards } from "../boards"
import { downloadReason } from "../downloadReasons"
import { judgeDrop, refuse } from "../drops"
import { useHandshake, useSetPinImage } from "../images"
import { m } from "../paraglide/messages.js"
import { useTagSearch, useUpdatePin, type Pin } from "../pins"

function Field({
  name,
  label,
  type,
  defaultValue,
}: {
  name: string
  label: string
  type?: "text" | "url"
  defaultValue: string
}) {
  return (
    <TextField name={name} type={type} defaultValue={defaultValue}>
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

/**
 * The image is replaced and never deleted: one `PUT` supersedes it, keeping the old bytes until
 * the new ones land, so nothing drops an image ahead of a fetch that can fail (decision M).
 */
function ImageControls({ pin }: { pin: Pin }) {
  const setImage = useSetPinImage()
  const limits = useHandshake().data?.limits
  const address = pin.sourceMediaUrl
  const replacement = pin.image?.replacement

  async function replace(file: File | undefined) {
    if (file === undefined) return
    const judged = await judgeDrop([file], "", limits)
    judged.refusals.forEach(refuse)
    const kept = judged.files[0]
    if (kept !== undefined) setImage.mutate({ pinId: pin.id, source: { file: kept.file } })
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {/* A file picker is no HeroUI control, so the label borrows the variant instead. */}
        <label className={buttonVariants({ variant: "secondary", size: "sm" })}>
          {m.replace_image()}
          <input
            type="file"
            accept={limits?.mediaTypes.join(",") ?? "image/*"}
            className="sr-only"
            onChange={(event) => {
              const input = event.currentTarget
              void replace(input.files?.[0])
              // An input still holding a file fires no change event for that same file.
              input.value = ""
            }}
          />
        </label>
        {/* Absent when the pin names no address, and worded as fetching from that address: an
            uploaded pin carries one too, and nothing here restores anything. */}
        {address !== null && (
          <Button
            size="sm"
            variant="secondary"
            isDisabled={setImage.isPending}
            onPress={() => setImage.mutate({ pinId: pin.id, source: { url: address } })}
          >
            {m.fetch_image_again()}
          </Button>
        )}
      </div>
      {/* The sub-state the contract has carried since before any client read it: the pin keeps the
          image it has while the server downloads the one asked for. */}
      {replacement?.status === "PENDING" && <p role="status">{m.image_replacing()}</p>}
      {replacement?.status === "FAILED" && (
        <p role="alert">{downloadReason(replacement.reasonCode, replacement.message)}</p>
      )}
      {setImage.isError && <p role="alert">{m.image_refused()}</p>}
    </div>
  )
}

/**
 * What edits a pin is a set of fields, where what shows one is an image and its words (decision K).
 * Every field is sent on every save: the route replaces the pin, and an empty list clears.
 */
export function PinEditForm({ pin, close }: { pin: Pin; close: () => void }) {
  const save = useUpdatePin()
  const [tags, setTags] = useState<readonly string[]>(pin.tags.map((tag) => tag.name))
  const [boardIds, setBoardIds] = useState<readonly string[]>(pin.boards.map((board) => board.id))

  return (
    <div className="flex flex-col gap-3">
      {/* Outside the form: the image is written by a route of its own, and a file input inside
          would join the body the save sends. */}
      <ImageControls pin={pin} />
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
                sourceMediaUrl: String(fields.get("sourceMediaUrl")) || null,
                tags: [...tags],
                boardIds: [...boardIds],
              },
            },
            { onSuccess: close },
          )
        }}
      >
        <TextField name="description" defaultValue={pin.description}>
          <Label>{m.description()}</Label>
          <TextArea rows={3} />
        </TextField>
        <Field
          name="sourceMediaUrl"
          type="url"
          label={m.image_address()}
          defaultValue={pin.sourceMediaUrl ?? ""}
        />
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
          <Button type="submit" isDisabled={save.isPending}>
            {m.save()}
          </Button>
        </div>
      </form>
    </div>
  )
}
