package fr.geoffreyCoulaud.pinryReborn.api.application

import java.io.File

/**
 * `src/main/resources/application.properties`, read from the file: the test resources share its name and override the
 * keys they declare, which is what a case comparing against a shipped value needs.
 */
object ProductionProperties {
    private const val PATH = "src/main/resources/application.properties"

    /** The declared value, or null when the file declares no such key. */
    operator fun get(key: String): String? =
        File(PATH)
            .readLines()
            .map { it.trim() }
            .filterNot { it.startsWith("#") || it.isEmpty() }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.take(separator).trim() to line.drop(separator + 1).trim()
            }
            .toMap()[key]
}
