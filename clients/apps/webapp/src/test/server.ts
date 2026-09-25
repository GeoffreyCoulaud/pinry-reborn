import { HttpResponse, http } from "msw"
import { setupServer } from "msw/node"

const NO_PAGE = { previousCursor: null, nextCursor: null }

/**
 * A journey declares the routes it needs and every other one is an error, but for the two lists
 * the task centre reads on every signed-in screen: they answer empty unless a journey says more.
 */
export const server = setupServer(
  http.get("/api/v1/me/exports", () => HttpResponse.json({ exports: [], pagination: NO_PAGE })),
  http.get("/api/v1/me/imports", () => HttpResponse.json({ imports: [], pagination: NO_PAGE })),
)
