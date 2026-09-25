# The web application's build stage: its image, and pnpm at the version `clients/package.json` pins.
# pnpm comes from npm and not corepack, which Node ships deprecated.
FROM node:26-slim@sha256:ec7758ee051e457b468b32bde57b0879010b325bb9862718e9615225ce4aaae1

RUN npm install --global pnpm@12.3.4
