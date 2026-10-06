rootProject.name = "pinry-reborn"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    ":api-domain",
    ":api-usecases",
    ":api-persistence-sqlite",
    ":api-presentation-quarkus",
    ":api-application",
    ":api-utilities",
    ":api-storage-filesystem",
    ":api-imaging-vips",
    ":api-video-ffmpeg",
    ":api-fetch-http",
    ":api-fetch-ytdlp",
    ":api-system",
    ":api-worker-quarkus",
    ":detekt-rules",
)
