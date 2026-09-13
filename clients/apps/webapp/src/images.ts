import type { Schemas } from "@pinry-reborn/auth"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef } from "react"
import { auth, bodyOf } from "./api"
import { downloadPollInterval, hasSettled, type DownloadProgress } from "./lib/downloads"

export type Download = Schemas["ImageDownloadOutputDto"]

/** Where a pin's image comes from: an address the server fetches, or bytes from disk. */
export type ImageSource = { url: string } | { file: File }

export interface PinCreation {
  sourceContextUrl: string | null
  description: string
  source: ImageSource
}

const DOWNLOADS = ["image-downloads"]
const PINS = ["pins"]
const PIN_IMAGE = "/api/v1/pins/{pinId}/image"

/**
 * One route for both entries, told apart by the request's media type: bytes answer with the image
 * itself, an address answers `202` and the download joins the task centre (specification 4.8).
 */
async function setPinImage(pinId: string, source: ImageSource): Promise<void> {
  const params = { path: { pinId } }
  const answer =
    "file" in source
      ? await auth.client.PUT(PIN_IMAGE, {
          params,
          body: { file: source.file as unknown as string },
          // A `FormData` body is what makes openapi-fetch drop its own `Content-Type`, so the
          // browser writes the multipart boundary the server reads.
          bodySerializer: () => {
            const form = new FormData()
            form.append("file", source.file)
            return form
          },
        })
      : await auth.client.PUT(PIN_IMAGE, { params, body: { sourceUrl: source.url } })
  bodyOf(answer, "the image")
}

async function dropDownload(pinId: string): Promise<void> {
  const { response } = await auth.client.DELETE("/api/v1/me/image-downloads/{pinId}", {
    params: { path: { pinId } },
  })
  if (!response.ok) throw new Error(`The API kept the download: ${response.status}.`)
}

/** The deployment's own limits, asked once: they are its configuration, not a user's state. */
export function useHandshake() {
  return useQuery({
    queryKey: ["handshake"],
    queryFn: async () => bodyOf(await auth.client.GET("/api/v1/handshake"), "the handshake"),
    staleTime: Infinity,
  })
}

/**
 * The downloads running or failed, reread while the server still has work. A settled one is the
 * only thing that changes a pin without the user touching it, so it is what rereads the grid.
 */
export function useImageDownloads() {
  const queryClient = useQueryClient()
  const previous = useRef<readonly DownloadProgress[]>([])
  const downloads = useQuery({
    queryKey: DOWNLOADS,
    queryFn: async () =>
      bodyOf(await auth.client.GET("/api/v1/me/image-downloads"), "the downloads").downloads,
    refetchInterval: (query) => downloadPollInterval(query.state.data),
  })

  useEffect(() => {
    const current = downloads.data ?? []
    if (hasSettled(previous.current, current)) {
      void queryClient.invalidateQueries({ queryKey: PINS })
    }
    previous.current = current
  }, [downloads.data, queryClient])

  return downloads
}

function useImageOutcome() {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: PINS })
    void queryClient.invalidateQueries({ queryKey: DOWNLOADS })
  }
}

/**
 * The pin first, then its image. Nothing here is optimistic: a pin created from an address owes
 * its image to a download the server has not run yet (specification 4.8).
 */
export function useCreatePin() {
  const settled = useImageOutcome()
  return useMutation({
    mutationFn: async ({ sourceContextUrl, description, source }: PinCreation) => {
      const body = {
        sourceContextUrl,
        sourceMediaUrl: "url" in source ? source.url : null,
        description,
      }
      const pin = bodyOf(await auth.client.POST("/api/v1/pins", { body }), "the pin")
      await setPinImage(pin.id, source)
    },
    onSuccess: settled,
  })
}

/** The recourse a failed download has: the same address again, or a file from disk (question V). */
export function useSetPinImage() {
  const settled = useImageOutcome()
  return useMutation({
    // The failed row is the server's to clear, and a successful upload clears it: a DELETE here
    // would answer 404 and reject a mutation that succeeded (`SetPinImage` calls `ClearPinDownload`).
    mutationFn: ({ pinId, source }: { pinId: string; source: ImageSource }) =>
      setPinImage(pinId, source),
    onSuccess: settled,
  })
}

export function useDropDownload() {
  const settled = useImageOutcome()
  return useMutation({ mutationFn: dropDownload, onSuccess: settled })
}
