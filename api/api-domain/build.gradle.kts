plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

dependencies {
    testFixturesImplementation(testFixtures(project(":api-utilities")))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The fixtures are tests' code, which no test of this module exercises.
extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
    currentProject { sources { excludedSourceSets.add("testFixtures") } }
}
