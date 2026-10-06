plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":api-domain"))
    implementation(project(":api-utilities"))
    // Jackson is provided at runtime by api-application's Quarkus runtime.
    compileOnly(platform(libs.quarkus.bom))
    compileOnly(libs.jackson.databind)
    testImplementation(platform(libs.quarkus.bom))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.bundles.testing.runtime)
}
