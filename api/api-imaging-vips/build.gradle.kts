plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":api-domain"))
    implementation(project(":api-utilities"))
    testImplementation(testFixtures(project(":api-utilities")))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.bundles.testing.runtime)
}
