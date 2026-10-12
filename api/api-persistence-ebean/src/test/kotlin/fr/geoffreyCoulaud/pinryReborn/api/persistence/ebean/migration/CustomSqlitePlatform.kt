package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.migration

import io.ebean.config.dbplatform.DbPlatformType
import io.ebean.config.dbplatform.DbType
import io.ebean.platform.sqlite.SQLitePlatform

/**
 * Custom SQLite platform
 * - maps VARCHAR to TEXT
 */
class CustomSqlitePlatform : SQLitePlatform() {
    init {
        dbTypeMap.put(DbType.VARCHAR, DbPlatformType("text"))
    }
}
