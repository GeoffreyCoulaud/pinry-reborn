import {
  createRootRoute,
  createRoute,
  createRouter,
  type RouterHistory,
} from "@tanstack/react-router"
import { SignIn, SignUp } from "./routes/Credentials"
import { Home } from "./routes/Home"

const rootRoute = createRootRoute()

const homeRoute = createRoute({ getParentRoute: () => rootRoute, path: "/", component: Home })
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
