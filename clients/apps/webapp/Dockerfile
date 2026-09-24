# $BUILDPLATFORM keeps the compile off the emulator in a two architecture build; what it emits is
# architecture independent, exactly as the API's fast jar is.
FROM --platform=$BUILDPLATFORM node:25-slim@sha256:81db02c4b671288a03915da9534dbd54f96d0e7c24d80ccc54f5b36b2e684370 AS build

RUN npm install --global pnpm@12.3.4

# The context is the repository root: @pinry-reborn/api-client generates its types from
# ../../../contract/openapi.json in its `prepare` script, so the install needs contract/ too.
WORKDIR /src
COPY contract/ contract/
COPY clients/ clients/

WORKDIR /src/clients
# Named to pnpm: on a mount of its own the store would otherwise fall back to node_modules/.pnpm-store.
RUN --mount=type=cache,target=/pnpm-store pnpm install --frozen-lockfile --store-dir /pnpm-store
RUN pnpm --filter @pinry-reborn/webapp run build

FROM nginx:1-alpine-slim@sha256:f761b94f2cb9e8e05e2943d5f773609596113ef69b54e2433a996d109a8f78b7
COPY clients/apps/webapp/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/clients/apps/webapp/dist/ /srv/
