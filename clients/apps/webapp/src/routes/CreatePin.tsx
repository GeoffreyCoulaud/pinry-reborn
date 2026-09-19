import { Button, Input, Label, TextField, inputVariants } from "@heroui/react"
import { useNavigate } from "@tanstack/react-router"
import { useState } from "react"
import { TaskCentre } from "../components/TaskCentre"
import { ThemeSwitch } from "../components/ThemeSwitch"
import { useCreatePin, useHandshake, type ImageSource } from "../images"
import { uploadRefusal, type UploadRefusal } from "../lib/uploads"
import { m } from "../paraglide/messages.js"

const REFUSALS: Record<UploadRefusal, () => string> = {
  TOO_MANY_BYTES: m.file_too_heavy,
  TOO_MANY_PIXELS: m.file_too_large,
}

/** The pixel count a limit is read against, which nothing short of a decoder knows. */
async function measured(file: File) {
  const bitmap = await createImageBitmap(file)
  return { size: file.size, width: bitmap.width, height: bitmap.height }
}

/**
 * Two entries, one screen: an address the server fetches, or a file from disk. The file is
 * measured against the deployment's limits here, so an oversized one costs no upload at all
 * (specification 4.8).
 */
export function CreatePin() {
  const navigate = useNavigate()
  const create = useCreatePin()
  const handshake = useHandshake()
  // The file is held here rather than read from the form: jsdom carries an uploaded file into
  // `new FormData(form)` with its bytes dropped, which makes every journey test a lie.
  const [file, setFile] = useState<File | null>(null)
  const [refused, setRefused] = useState<UploadRefusal | null>(null)

  async function submit(fields: FormData) {
    let source: ImageSource = { url: String(fields.get("sourceMediaUrl")) }
    if (file !== null) {
      const refusal = uploadRefusal(await measured(file), handshake.data?.limits)
      setRefused(refusal)
      if (refusal !== null) return
      source = { file }
    }
    create.mutate(
      {
        sourceContextUrl: String(fields.get("sourceContextUrl")) || null,
        description: String(fields.get("description")),
        source,
      },
      { onSuccess: () => void navigate({ to: "/" }) },
    )
  }

  return (
    <main className="mx-auto flex max-w-sm flex-col gap-4 p-8">
      {/* A download requested here keeps running past the navigation, so the screen that starts it
          is the one that must show it. */}
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-separator pb-3">
        <h1 className="text-2xl font-semibold">{m.create_pin()}</h1>
        <div className="flex flex-wrap items-center gap-2">
          <TaskCentre />
          <ThemeSwitch />
        </div>
      </header>
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          void submit(new FormData(event.currentTarget))
        }}
      >
        {/* Never required: a file from disk and a direct image address both name no page. */}
        <TextField name="sourceContextUrl" type="url">
          <Label>{m.source_page()}</Label>
          <Input />
        </TextField>
        <TextField name="description">
          <Label>{m.description()}</Label>
          <Input />
        </TextField>
        {/* The address stops being required once a file is chosen: an image comes from one or the other. */}
        <TextField name="sourceMediaUrl" type="url" isRequired={file === null}>
          <Label>{m.image_address()}</Label>
          <Input />
        </TextField>
        <label className="flex flex-col gap-1">
          {m.image_file()}
          <input
            name="file"
            type="file"
            accept="image/*"
            onChange={(event) => setFile(event.currentTarget.files?.[0] ?? null)}
            className={inputVariants()}
          />
        </label>
        {refused !== null && <p role="alert">{REFUSALS[refused]()}</p>}
        {create.isError && <p role="alert">{m.creation_refused()}</p>}
        {/* Submitting before the limits arrive would send a file this deployment refuses. */}
        <Button type="submit" isDisabled={create.isPending || handshake.isPending}>
          {m.create_pin()}
        </Button>
      </form>
    </main>
  )
}
