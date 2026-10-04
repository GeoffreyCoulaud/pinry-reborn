plugins {
    alias(libs.plugins.kotlin.jvm)
}
dependencies {
    implementation(project(":api-domain"))
    implementation(project(":api-utilities"))
    implementation(libs.vips.ffm.core)
    testImplementation(testFixtures(project(":api-utilities")))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.bundles.testing.runtime)
}
