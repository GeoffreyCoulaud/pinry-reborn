FROM denoland/deno:bin-2.9.7@sha256:bc5aa4466e21b6d3021226a85ba2e1911f7c386254d97b9d797903ab74edace2 AS deno

# The JDK is the toolchain the build asks for, so Gradle adopts it instead of provisioning one;
# libvips-tools brings the vipsheader and vips api-imaging-vips runs; ffmpeg brings the ffprobe and ffmpeg api-video-ffmpeg's tests run; yt-dlp and deno as api/Dockerfile has them.
FROM eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc

RUN apt-get update && apt-get install -y --no-install-recommends libvips-tools ffmpeg python3 python3-venv

COPY --from=deno /deno /usr/local/bin/deno
COPY tools/yt-dlp/requirements.txt /opt/yt-dlp/requirements.txt
RUN python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --require-hashes --no-cache-dir -r /opt/yt-dlp/requirements.txt
ENV PATH="/opt/yt-dlp/bin:${PATH}"
