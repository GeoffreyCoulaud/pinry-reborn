import { createApiClient, type ApiClient, type components, type paths } from "@pinry-reborn/api-client"

/** The vehicle the session token travels in. The web application takes the cookie, the extension the header. */
export type SessionTransport = components["schemas"]["SessionTransportDto"]

export type Credentials = components["schemas"]["UserInputDto"]

/**
 * The contract's own types, so an application names a response without depending on the package a
 * generator rewrites at every install.
 */
export type Schemas = components["schemas"]

export type { Known } from "@pinry-reborn/api-client"

/** Every `code` the contract declares one operation's refusals can carry, shared entries included. */
export type RefusalCode<Path extends keyof paths, Method extends keyof paths[Path]> =
  paths[Path][Method] extends { responses: infer Responses }
    ? {
        [Status in keyof Responses]: Responses[Status] extends {
          content: { "application/problem+json": { code?: infer Code } }
        }
          ? Code extends string ? Code : never
          : never
      }[keyof Responses]
    : never

/**
 * What both transports agree on. A bearer answer also carries the token, which never leaves this
 * package: the application above holds no credential whatever the transport
 * (docs/adr/0026-one-session-two-transports.md, decision 1).
 */
export interface Session { expiresAt: string; renewAfter: string }

export interface AuthOptions {
  transport: SessionTransport
  /** The origin the API answers on: its own for a caller that shares it, the deployment's for the extension. */
  baseUrl: string
}

export interface Auth {
  /**
   * The typed client every other route goes through, carrying whichever transport this session
   * was opened with (specification 4.6). An application holds no client of its own.
   */
  readonly client: ApiClient
  /** Creates the account and opens its first session, so a fresh instance signs the user in once. */
  signUp(credentials: Credentials, rememberMe?: boolean): Promise<Session>
  signIn(credentials: Credentials, rememberMe?: boolean): Promise<Session>
  signOut(): Promise<void>
  /**
   * Stops believing in the session, and calls nothing: the API has already revoked it. Left out
   * after such a write, the middleware below spends a renewal on the next request
   * (specification 2026-09-22, decision E).
   */
  forget(): void
  /**
   * The session the request already carries, or null when the API answers `401`. Any other
   * refusal throws: a deployment that is briefly unreachable has not ended the session.
   */
  currentSession(): Promise<Session | null>
}

const UNAUTHORISED = 401
const RENEWAL = "/api/v1/sessions/current/renew"

export function createAuth({ transport, baseUrl }: AuthOptions): Auth {
  const client: ApiClient = createApiClient(baseUrl)
  let token: string | undefined
  let renewAfter: string | undefined
  let renewal: Promise<Session> | undefined

  client.use({
    async onRequest({ request, schemaPath }) {
      if (schemaPath !== RENEWAL) await renewIfDue()
      // After the renewal, never before: a bearer renewal answers a new token.
      if (token !== undefined) request.headers.set("Authorization", `Bearer ${token}`)
      return request
    },
  })

  /** A cookie answer is 200 and a bearer answer 201, so the token is absent by shape and not by a
   * nullable field (docs/adr/0026-one-session-two-transports.md, decision 4). */
  function adopt(session: Schemas["CreatedSessionOutputDto"] | Schemas["ExistingSessionOutputDto"]): Session {
    if ("token" in session) token = session.token
    renewAfter = session.renewAfter
    return { expiresAt: session.expiresAt, renewAfter: session.renewAfter }
  }

  async function renewIfDue(): Promise<void> {
    if (renewAfter === undefined || Date.parse(renewAfter) > Date.now()) return
    // Shared, a page load leaving with several calls at once; swallowed, only the API's 401 ending a session.
    renewal ??= renew().finally(() => (renewal = undefined))
    await renewal.catch(() => {})
  }

  async function renew(): Promise<Session> {
    const { data, response } = await client.POST(RENEWAL)
    if (data === undefined) throw new Error(`The API refused the renewal: ${response.status}.`)
    return adopt(data)
  }

  async function openSession(credentials: Credentials, rememberMe: boolean): Promise<Session> {
    const { data, response } = await client.POST("/api/v1/sessions", {
      body: { ...credentials, transport, rememberMe },
    })
    if (data === undefined) throw new Error(`The API refused the session: ${response.status}.`)
    return adopt(data)
  }

  function forget() {
    token = undefined
    renewAfter = undefined
  }

  return {
    client,
    forget,
    async signUp(credentials, rememberMe = false) {
      const { data, response } = await client.POST("/api/v1/users", { body: credentials })
      if (data === undefined) throw new Error(`The API refused the account: ${response.status}.`)
      return openSession(credentials, rememberMe)
    },
    signIn: (credentials, rememberMe = false) => openSession(credentials, rememberMe),
    async signOut() {
      // The token goes whatever the answer: a revocation the API refuses is still a user who
      // asked to leave, and the cookie is the server's to clear.
      await client.DELETE("/api/v1/sessions/current")
      token = undefined
      renewAfter = undefined
    },
    async currentSession() {
      const { data, response } = await client.GET("/api/v1/sessions/current")
      // Only a 401 is an answer about the session. openapi-fetch leaves `data` undefined for a
      // 500 or a 503 just as readily, and reading those as no session signs the user out on a
      // failure the next request would survive.
      if (response.status === UNAUTHORISED) return null
      if (data === undefined) throw new Error(`The API refused the session: ${response.status}.`)
      return adopt(data)
    },
  }
}
