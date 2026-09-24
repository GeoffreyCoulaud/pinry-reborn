# The web application's build stage: its image, and pnpm at the version `clients/package.json` pins.
# pnpm comes from npm and not corepack, which Node ships deprecated.
FROM node:24-slim@sha256:0e0ff40c39bc087845bfb27465a0df4ea419520094bc35842ff83dd8cbe6f9b6

RUN npm install --global pnpm@12.3.4
