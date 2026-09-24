# The JDK is the toolchain the build asks for, so Gradle adopts it instead of provisioning one;
# libvips is what vips-ffm loads, under the t64 name Ubuntu gives it after the 64-bit time_t transition.
FROM eclipse-temurin:25-jdk@sha256:97014c4b396021f9ddb7d592a7dbedb0c4e4215c29e03dc01c393558aefb71c2

RUN apt-get update && apt-get install -y --no-install-recommends libvips42t64
