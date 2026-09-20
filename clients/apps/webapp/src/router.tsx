import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
  type SearchSchemaInput,
} from "@tanstack/react-router"
import { pinSortOr } from "./lib/sorts"
import { SignIn, SignUp } from "./routes/Credentials"
import { Home } from "./routes/Home"

const rootRoute = createRootRoute()

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  component: Home,
  // `SearchSchemaInput` is what marks the order optional to a caller: without it the router
  // reads the validator's output as its input and every `Navigate` here has to carry a sort.
  validateSearch: (search: { sort?: string } & SearchSchemaInput) => ({
    sort: pinSortOr(search.sort),
  }),
})
const signInRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sign-in", component: SignIn })
const signUpRoute = createRoute({ getParentRoute: () => rootRoute, path: "/sign-up", component: SignUp })

const routeTree = rootRoute.addChildren([homeRoute, signInRoute, signUpRoute])

/** The history is an argument so a test can drive the router without a browser. */
export function createAppRouter(history?: RouterHistory) {
  return createRouter({ routeTree, history })
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>
  }
}
