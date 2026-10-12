package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

import io.avaje.config.Config

/**
 * Refuses the boot when the database's password names an unset variable: avaje-config leaves `${DB_PASSWORD}` as its
 * literal text, which a server trusting the connection would accept.
 */
object DatasourcePasswordCheck {
    private const val PASSWORD_KEY = "datasource.db.password"
    private val PLACEHOLDER = Regex("""\$\{([^}]+)}""")

    /**
     * Reads the password from avaje-config, as Ebean does. Not through `Database.builder().loadFromProperties()`, which
     * spends ebean-test's run-once setup on a builder nobody builds.
     */
    fun verifyConfigured() = verifyResolved(Config.getNullable(PASSWORD_KEY))

    fun verifyResolved(password: String?) {
        val placeholder = PLACEHOLDER.matchEntire(password.orEmpty()) ?: return
        error("$PASSWORD_KEY is unresolved: set the environment variable ${placeholder.groupValues[1]}")
    }
}
