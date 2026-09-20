import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
  type SearchSchemaInput,
} from "@tanstack/react-router"
import { pinSortOr } from "./lib/sorts"
import { Board } from "./routes/Board"
import { Boards } from "./routes/Boards"
import { SignIn, SignUp } from "./routes/Credentials"
import { Home } from "./routes/Home"

const rootRoute = createRootRoute()

/**
 * Each grid reads its order from its own address. `SearchSchemaInput` is what marks the order
 * optional to a caller: without it the router reads the validator's output as its input and every
 * `Navigate` here has to carry a sort.
 */
function validateSort(search: { sort?: string } & SearchSchemaInput) {
  return { sort: pinSortOr(search.sort) }
}

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: Home,
  validateSearch: validateSort,
})
const boardsRoute = createRoute({ getParentRoute: () => rootRoute, path: "/boards", component: Boards })
const boardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/boards/$boardId",
  component: Board,
  validateSearch: validateSort,
})
const signInRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sign-in", component: SignIn })
const signUpRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sign-up", component: SignUp })

const routeTree = rootRoute.addChildren([
  homeRoute,
  boardsRoute,
  boardRoute,
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
