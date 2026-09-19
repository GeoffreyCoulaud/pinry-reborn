import { Badge, Button, Popover, Tooltip, buttonVariants } from "@heroui/react"
import { Download as DownloadIcon } from "lucide-react"
import { downloadReason } from "../downloadReasons"
import { useDropDownload, useImageDownloads, useSetPinImage, type Download } from "../images"
import { m } from "../paraglide/messages.js"

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
 * what it failed to. A success leaves nothing here, its result being the pin (question J).
 */
export function TaskCentre() {
  const page = useImageDownloads().data
  const downloads = page?.downloads ?? []
  const partial = page?.hasMore === true
  const label = partial
    ? m.downloads_partial({ count: downloads.length })
    : m.downloads({ count: downloads.length })

  return (
    <Popover>
      <Badge.Anchor>
        <Tooltip>
          <Button variant="ghost" isIconOnly aria-label={label}>
            <DownloadIcon aria-hidden />
          </Button>
          <Tooltip.Content>{label}</Tooltip.Content>
        </Tooltip>
        {/* The name above already carries the count; a badge read as well would say it twice. */}
        {downloads.length > 0 && (
          <Badge aria-hidden>{`${downloads.length}${partial ? "+" : ""}`}</Badge>
        )}
      </Badge.Anchor>
      <Popover.Content className="max-w-sm">
        <Popover.Dialog aria-label={label}>
          {downloads.length === 0 ? (
            <p>{m.downloads_empty()}</p>
          ) : (
            <ul>
              {downloads.map((download) => (
                <Task key={download.pinId} download={download} />
              ))}
            </ul>
          )}
        </Popover.Dialog>
      </Popover.Content>
    </Popover>
  )
}
