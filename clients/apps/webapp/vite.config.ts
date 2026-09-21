import { paraglideVitePlugin } from "@inlang/paraglide-js"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vitest/config"

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    paraglideVitePlugin({
      project: "./project.inlang",
      outdir: "./src/paraglide",
      emitTsDeclarations: true,
    }),
  ],
  // The proxy is what makes the application same origin in development, which is where the
  // cookie of ADR 0026 reaches the API (docs/specs/2026-09-10-web-application.md, section 8).
  server: { proxy: { "/api": "http://localhost:8080" } },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    // The slowest case measures 955 ms here and a GitHub runner is about ten times slower, so the
    // default 5000 ms sits under what is expected there (specification 2026-09-21, block 25).
    testTimeout: 15_000,
    coverage: {
      provider: "v8",
      // The bound covers the pure functions and not the view, which jsdom renders
      // without laying out (specification section 4.6, revising ADR 0024 decision 11).
      include: ["src/lib/**/*.ts"],
      thresholds: { lines: 100, branches: 100 },
    },
  },
})
