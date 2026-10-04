plugins {
    alias(libs.plugins.kotlin.jvm)
}
dependencies {
    implementation(project(":api-domain"))
    implementation(project(":api-fetch-http"))
    implementation(project(":api-utilities"))
    // Jackson is provided at runtime by api-application's Quarkus runtime.
    compileOnly(platform(libs.quarkus.bom))
    compileOnly(libs.jackson.databind)
    testImplementation(project(":api-video-ffmpeg"))
    testImplementation(platform(libs.quarkus.bom))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.bundles.testing.runtime)
}
// The video fixtures are api-video-ffmpeg's, served here as a page's media.
sourceSets.test { resources.srcDir(project(":api-video-ffmpeg").file("src/test/resources")) }
