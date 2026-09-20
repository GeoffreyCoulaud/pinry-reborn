import { Toast } from "@heroui/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RouterProvider } from "@tanstack/react-router"
import { StrictMode } from "react"
import { I18nProvider } from "react-aria-components"
import { createRoot } from "react-dom/client"
import { getLocale } from "./paraglide/runtime.js"
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
    {/* react-aria reads `navigator.language` otherwise, and the application is what paraglide says. */}
    <I18nProvider locale={getLocale()}>
      <QueryClientProvider client={new QueryClient()}>
        <RouterProvider router={createAppRouter()} />
        {/* Mounted once here so anything calling `toast()` needs no provider of its own (ADR 0037). */}
        <Toast.Provider />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
)
