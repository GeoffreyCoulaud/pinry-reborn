import createOpenApiClient, { type Client } from "openapi-fetch"
import type { extensibleEnums, paths } from "./schema"

export type { components, paths } from "./schema"

/**
 * The values an `x-extensible-enum` component knows today, its field staying `string`: a table keyed
 * by them fails to compile when it misses one.
 */
export type Known<Name extends keyof extensibleEnums> = extensibleEnums[Name]

/** The typed client `openapi-fetch` builds over the contract's paths. */
export type ApiClient = Client<paths>

/**
 * The client every caller goes through. `baseUrl` is stated rather than defaulted: an empty one
 * builds a relative `Request`, which a browser resolves against the document and Node refuses
 * outright, so the default that reads best is the one that fails in every test.
 *
 * This package knows nothing about sessions, because what a generator rewrites at every
 * install cannot sit downstream of code written by hand
 * (docs/specs/2026-09-10-web-application.md, section 4.6).
 */
export function createApiClient(baseUrl: string): ApiClient {
  return createOpenApiClient<paths>({ baseUrl })
}
