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
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => auth.signOut(),
    onSuccess: () => queryClient.setQueryData(SESSION_KEY, null),
  })
}

/**
 * What the writes the API revokes a session inside of leave to do: stop believing in one. No
 * cookie is cleared here, only `DELETE /api/v1/sessions` clearing `pinry_session`
 * (specification 2026-09-22, decision C).
 */
export function useEndSession() {
  const queryClient = useQueryClient()
  return () => {
    auth.forget()
    queryClient.setQueryData(SESSION_KEY, null)
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
