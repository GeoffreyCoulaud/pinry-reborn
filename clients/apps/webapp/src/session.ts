import type { Credentials, Session } from "@pinry-reborn/auth"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { auth } from "./api"

const SESSION_KEY = ["session"]

export interface OpenSession { credentials: Credentials; rememberMe: boolean }

/** The session the browser's cookie carries, or null once the API stops honouring it. */
export function useSession() {
  // A refusal is an answer and not a failure to retry: an expired session never becomes valid again.
  return useQuery({ queryKey: SESSION_KEY, queryFn: () => auth.currentSession(), retry: false })
}

function useOpenSession(open: (credentials: Credentials, rememberMe: boolean) => Promise<Session>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ credentials, rememberMe }: OpenSession) => open(credentials, rememberMe),
    onSuccess: (session) => queryClient.setQueryData(SESSION_KEY, session),
  })
}

export type OpenSessionMutation = ReturnType<typeof useOpenSession>

export function useSignIn(): OpenSessionMutation {
  return useOpenSession((credentials, rememberMe) => auth.signIn(credentials, rememberMe))
}

export function useSignUp(): OpenSessionMutation {
  return useOpenSession((credentials, rememberMe) => auth.signUp(credentials, rememberMe))
}

export function useSignOut() {
  const endSession = useEndSession()
  return useMutation({
    mutationFn: () => auth.signOut(),
    onSuccess: endSession,
  })
}

/**
 * Every way a session ends, ordinary sign out included. No cookie is cleared here, only
 * `DELETE /api/v1/sessions` clearing `pinry_session` (specification 2026-09-22, decision C).
 */
export function useEndSession() {
  const queryClient = useQueryClient()
  return () => {
    auth.forget()
    queryClient.setQueryData(SESSION_KEY, null)
    // Everything else goes: the client outlives the credentials screen, and an inactive query is
    // kept five minutes, so the next account signing in on this browser is painted the previous
    // one's pins and name until its own requests land. The session is what the guard then reads,
    // and `clear()` would take it with the rest: a removed query leaves its observer watching a
    // destroyed one, so nothing re-renders and no redirect happens.
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== SESSION_KEY[0] })
  }
}

export function useSignOutEverywhere() {
  const endSession = useEndSession()
  return useMutation({
    mutationFn: async () => {
      const { response } = await auth.client.DELETE("/api/v1/sessions")
      if (!response.ok) throw new Error(`The API kept the sessions: ${response.status}.`)
    },
    onSuccess: endSession,
  })
}
