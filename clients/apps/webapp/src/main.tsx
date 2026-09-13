import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RouterProvider } from "@tanstack/react-router"
import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { createAppRouter } from "./router"
import "./styles.css"
import { paintStoredTheme } from "./theme"

const container = document.getElementById("root")
if (!container) {
  throw new Error("index.html has no #root element to mount the application into.")
}

paintStoredTheme()

createRoot(container).render(
  <StrictMode>
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={createAppRouter()} />
    </QueryClientProvider>
  </StrictMode>,
)
