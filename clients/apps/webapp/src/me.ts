import type { Schemas } from "@pinry-reborn/auth"
import { useMutation, useQuery } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"
import { passwordFactor } from "./lib/reauthentication"
import { refusalCode } from "./lib/refusals"
import { useEndSession } from "./session"

/**
 * A write the API refused, carrying the `code` of the problem body it answered with. The screen
 * turns that into a sentence; the status is not enough, two refusals sharing a 429
 * (specification 2026-09-22, decision D). The mutations below declare no error type: a fetch that
 * never reached the API rejects with a `TypeError`, and a generic would promise otherwise.
 */
export class AccountRefusal extends Error {
  readonly code: string | null
  constructor(error: unknown, status: number) {
    super(`The API refused the account: ${status}.`)
    this.code = refusalCode(error)
  }
}

/**
 * The account the session belongs to. `UserOutputDto` is an id and a name, so this is the one read
 * the account screen makes (specification 2026-09-22, decision A).
 */
export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: async () => bodyOf(await auth.client.GET("/api/v1/me"), "the account"),
  })
}

/**
 * The new password, and the session that held the old one. The API revokes inside the write's
 * transaction, so `endSession` records what has already happened rather than asking for it
 * (specification 2026-09-22, decision C).
 */
export function useChangePassword() {
  const endSession = useEndSession()
  return useMutation({
    mutationFn: async (body: Schemas["PasswordChangeInputDto"]) => {
      // `response.ok` and never `data`: a 204 leaves it undefined whatever the write did.
      const { error, response } = await auth.client.PUT("/api/v1/me/password", { body })
      if (!response.ok) throw new AccountRefusal(error, response.status)
    },
    onSuccess: endSession,
  })
}

/** The account, on the password the dialog asked for: the factor `X-Reauthentication` requires. */
export function useDeleteAccount() {
  const endSession = useEndSession()
  return useMutation({
    mutationFn: async (password: string) => {
      const { error, response } = await auth.client.DELETE("/api/v1/me", {
        params: { header: { "X-Reauthentication": passwordFactor(password) } },
      })
      if (!response.ok) throw new AccountRefusal(error, response.status)
    },
    onSuccess: endSession,
  })
}
