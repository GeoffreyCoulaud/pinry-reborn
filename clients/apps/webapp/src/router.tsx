import {
  Navigate,
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
  type SearchSchemaInput,
} from "@tanstack/react-router"
import type { ComponentType } from "react"
import { searchTermOr } from "./lib/searches"
import { pinSortOr, recycledPinSortOr } from "./lib/sorts"
import { m } from "./paraglide/messages.js"
import { Account } from "./routes/Account"
import { Board } from "./routes/Board"
import { Boards } from "./routes/Boards"
import { SignIn, SignUp } from "./routes/Credentials"
import { Home } from "./routes/Home"
import { Recycled } from "./routes/Recycled"
import { useSession } from "./session"

const rootRoute = createRootRoute()

/**
 * The session every screen but the credentials one is behind. It wraps at the declaration rather
 * than inside each body: a component around a screen's own JSX would re-indent four whole files
 * (specification 2026-09-22, decision F).
 */
function guarded(Screen: ComponentType) {
  return function Guarded() {
    const session = useSession()

    if (session.isPending) return null
    // A session the API could not answer for is not an expired one, and only the second sends the
    // user back to the credentials screen.
    if (session.isError) return <p role="alert">{m.session_unreadable()}</p>
    if (!session.data) return <Navigate to="/sign-in" />
    return <Screen />
  }
}

/**
 * Each grid reads its order and its search term from its own address. `SearchSchemaInput` is what
 * marks them optional to a caller: without it the router reads the validator's output as its input
 * and every `Navigate` here has to carry a sort.
 */
function validateGrid(search: { sort?: string; q?: string } & SearchSchemaInput) {
  return { sort: pinSortOr(search.sort), q: searchTermOr(search.q) }
}

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: guarded(Home),
  validateSearch: validateGrid,
})
const boardsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/boards",
  component: guarded(Boards),
})
const boardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/boards/$boardId",
  component: guarded(Board),
  validateSearch: validateGrid,
})
/** The bin's Pins tab serves one order the grid does not, and it is the bin's default. */
const recycledRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/recycled",
  component: guarded(Recycled),
  validateSearch: (search: { sort?: string } & SearchSchemaInput) => ({
    sort: recycledPinSortOr(search.sort),
  }),
})
const accountRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/account",
  component: guarded(Account),
})
const signInRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sign-in", component: SignIn })
const signUpRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sign-up", component: SignUp })

const routeTree = rootRoute.addChildren([
  homeRoute,
  boardsRoute,
  boardRoute,
  recycledRoute,
  accountRoute,
  signInRoute,
  signUpRoute,
])

/** The history is an argument so a test can drive the router without a browser. */
export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree, history })
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
