import { useQuery } from "@tanstack/react-query"
import { auth, bodyOf } from "./api"

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
