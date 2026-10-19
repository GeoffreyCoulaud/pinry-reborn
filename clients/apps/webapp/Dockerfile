# $BUILDPLATFORM keeps the compile off the emulator in a two architecture build; what it emits is
# architecture independent, exactly as the API's fast jar is.
FROM --platform=$BUILDPLATFORM node:26-slim@sha256:ec7758ee051e457b468b32bde57b0879010b325bb9862718e9615225ce4aaae1 AS build

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

FROM nginx:1-alpine-slim@sha256:ebd058041a732a2b2dc0b606e34572dd8e0034de6a4aa4d50d8c13a240ac6005
COPY clients/apps/webapp/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/clients/apps/webapp/dist/ /srv/
