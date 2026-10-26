FROM denoland/deno:bin-2.9.8@sha256:b507583d3c68ba69f3ed2ce5969ae5b8b70f43421c781279c7aea06435263543 AS deno

# The JDK is the toolchain the build asks for, so Gradle adopts it instead of provisioning one;
# libvips-tools brings the vipsheader and vips api-imaging-vips runs; ffmpeg brings the ffprobe and ffmpeg api-video-ffmpeg's tests run; yt-dlp and deno as api/Dockerfile has them.
FROM eclipse-temurin:25-jdk@sha256:97014c4b396021f9ddb7d592a7dbedb0c4e4215c29e03dc01c393558aefb71c2

RUN apt-get update && apt-get install -y --no-install-recommends libvips-tools ffmpeg python3 python3-venv

COPY --from=deno /deno /usr/local/bin/deno
COPY tools/yt-dlp/requirements.txt /opt/yt-dlp/requirements.txt
RUN python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --require-hashes --no-cache-dir -r /opt/yt-dlp/requirements.txt
ENV PATH="/opt/yt-dlp/bin:${PATH}"
