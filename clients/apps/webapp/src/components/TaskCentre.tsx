import { Badge, Button, Popover, buttonVariants } from "@heroui/react"
import { ArrowDownUp } from "lucide-react"
import { downloadReason } from "../downloadReasons"
import { useDropDownload, useImageDownloads, useSetPinImage, type Download } from "../images"
import { m } from "../paraglide/messages.js"
import { useDataTasks } from "./DataTasks"
import { IconButton } from "./IconButton"

/** A failed download offers what question V exists for: the same address again, or a file. */
function Task({ download }: { download: Download }) {
  const setImage = useSetPinImage()
  const drop = useDropDownload()
  const failed = download.status === "FAILED"
  const reason = downloadReason(download.reasonCode, download.message)

  return (
    <li className="flex flex-col gap-1 border-b border-separator py-2 last:border-0">
      <span className="font-medium">{failed ? m.task_failed() : m.task_running()}</span>
      <span className="truncate text-sm opacity-70">{download.sourceUrl}</span>
      {reason !== null && <span className="text-sm">{reason}</span>}
      {failed && (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            size="sm"
            onPress={() =>
              setImage.mutate({ pinId: download.pinId, source: { url: download.sourceUrl } })
            }
          >
            {m.retry()}
          </Button>
          {/* A file picker is no HeroUI control, so the label borrows the variant instead. */}
          <label className={buttonVariants({ variant: "secondary", size: "sm" })}>
            {m.image_file()}
            <input
              type="file"
              accept="image/*"
              className="sr-only"
              onChange={(event) => {
                const file = event.currentTarget.files?.[0]
                if (file) setImage.mutate({ pinId: download.pinId, source: { file } })
              }}
            />
          </label>
          <Button size="sm" variant="ghost" onPress={() => drop.mutate(download.pinId)}>
            {m.dismiss()}
          </Button>
        </div>
      )}
      {/* A refused action is silent otherwise, which is what the creation screen already avoids. */}
      {setImage.isError && <p role="alert">{m.image_refused()}</p>}
      {drop.isError && <p role="alert">{m.dismissal_refused()}</p>}
    </li>
  )
}

/**
 * The indicator the header carries, and the list behind it: what the server is downloading and
 * what it failed to, then the data tasks. A download's success leaves nothing here, its result
 * being the pin (specification 2026-09-10, question J).
 */
export function TaskCentre() {
  const page = useImageDownloads().data
  const downloads = page?.downloads ?? []
  const partial = page?.hasMore === true
  const data = useDataTasks()
  const count = downloads.length + data.length
  const label = partial ? m.tasks_partial({ count }) : m.tasks({ count })

  return (
    <Popover>
      <Badge.Anchor>
        <IconButton icon={ArrowDownUp} name={label} variant="ghost" />
        {/* The name above already carries the count; a badge read as well would say it twice. */}
        {count > 0 && <Badge aria-hidden>{`${count}${partial ? "+" : ""}`}</Badge>}
      </Badge.Anchor>
      {/* Narrower than a phone's width, or the popover sits flush against its right edge. */}
      <Popover.Content className="max-w-[min(24rem,calc(100vw-1.5rem))]">
        <Popover.Dialog aria-label={label}>
          {count === 0 ? (
            <p>{m.tasks_empty()}</p>
          ) : (
            <ul>
              {downloads.map((download) => (
                <Task key={download.pinId} download={download} />
              ))}
              {data}
            </ul>
          )}
        </Popover.Dialog>
      </Popover.Content>
    </Popover>
  )
}
