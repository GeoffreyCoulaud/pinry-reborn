# $BUILDPLATFORM keeps the compile off the emulator in a two architecture build; what it emits is
# architecture independent, exactly as the API's fast jar is.
FROM --platform=$BUILDPLATFORM node:24-slim AS build

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

FROM nginx:1-alpine-slim
COPY clients/apps/webapp/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/clients/apps/webapp/dist/ /srv/
