import type { Schemas } from "@pinry-reborn/auth"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import { POLL_MS } from "./lib/downloads"
import { passwordFactor } from "./lib/reauthentication"
import { AccountRefusal } from "./me"

export type Export = Schemas["UserDataExportOutputDto"]

const LATEST = ["exports", "latest"]

/**
 * The newest export or none, read as the first row of the list, which is newest first: the screen
 * shows no history (specification 2026-09-25, decision J). Reread while it is being prepared.
 */
export function useLatestExport() {
  return useQuery({
    queryKey: LATEST,
    queryFn: async () => {
      const query = { pageSize: 1 }
      const answer = await auth.client.GET("/api/v1/me/exports", { params: { query } })
      return bodyOf(answer, "the exports").exports[0] ?? null
    },
    refetchInterval: (query) => (query.state.data?.state === "PENDING" ? POLL_MS : false),
  })
}

/** A new export, on the password the dialog asked for: the factor `X-Reauthentication` requires. */
export function useRequestExport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (password: string) => {
      const { error, response } = await auth.client.POST("/api/v1/me/exports", {
        params: { header: { "X-Reauthentication": passwordFactor(password) } },
      })
      if (!response.ok) throw new AccountRefusal(error, response.status)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LATEST }),
  })
}

export function useDeleteExport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      // `response.ok` and never `data`: a 204 leaves it undefined.
      const { response } = await auth.client.DELETE("/api/v1/me/exports/{id}", {
        params: { path: { id } },
      })
      if (!response.ok) throw new Error(`The API kept the export: ${response.status}.`)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: LATEST }),
  })
}
