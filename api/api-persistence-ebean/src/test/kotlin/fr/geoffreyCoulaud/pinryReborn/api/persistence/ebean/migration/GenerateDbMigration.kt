package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.migration

import io.ebean.dbmigration.DbMigration
import io.ebean.platform.postgres.PostgresPlatform

/**
 * Generates database migration scripts.
 *
 * Run with: ./gradlew :api-persistence-ebean:generateDbMigration The migration files will be created in
 * src/main/resources/dbmigration/
 */
fun main() {
    val migration = DbMigration.create()

    migration.setPlatform(PostgresPlatform())
    migration.setPathToResources("src/main/resources")
    migration.setMigrationPath("dbmigration")

    migration.generateMigration()
}
