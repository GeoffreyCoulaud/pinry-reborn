import { useState, type ReactNode } from "react"
import { judgeDrop, refuse } from "../drops"
import { dragDepth } from "../lib/drags"
import type { DropPartition } from "../lib/drops"
import type { UploadLimits } from "../lib/uploads"
import { m } from "../paraglide/messages.js"

/**
 * The box both dialogs take an image through: the drop, the file picker, and the judging that
 * happens before a byte is shown. What a caller keeps of a drop differs, so the verdict is handed
 * over whole and the row describing the chosen file is the caller's own children.
 */
export function ImageDropBox({
  limits,
  multiple = false,
  onDrop,
  children,
}: {
  limits: UploadLimits | undefined
  multiple?: boolean
  onDrop: (drop: DropPartition) => void
  children?: ReactNode
}) {
  const [depth, setDepth] = useState(0)

  async function take(files: readonly File[], uriList: string) {
    const drop = await judgeDrop(files, uriList, limits)
    drop.refusals.forEach(refuse)
    onDrop(drop)
  }

  return (
    // `data-dragging` carries the counter rather than a class, jsdom computing no style: it is
    // what the active style hangs on and the only thing a test can read.
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
      {/* The invitation is the input's accessible name, which is what Label in Name asks for. Its
          hit area is stretched over the whole box without the thumbnail joining that name. */}
      <label className="cursor-pointer before:absolute before:inset-0 before:content-['']">
        {m.drop_image()}
        <input
          type="file"
          accept={limits?.mediaTypes.join(",") ?? "image/*"}
          multiple={multiple}
          className="sr-only"
          onChange={(event) => {
            const input = event.currentTarget
            // What is chosen with the mouse is judged where what is dropped is.
            void take([...(input.files ?? [])], "")
            // An input still holding a file fires no change event for that same file, so the one
            // just removed could never be chosen again.
            input.value = ""
          }}
        />
      </label>
      {children}
    </div>
  )
}
