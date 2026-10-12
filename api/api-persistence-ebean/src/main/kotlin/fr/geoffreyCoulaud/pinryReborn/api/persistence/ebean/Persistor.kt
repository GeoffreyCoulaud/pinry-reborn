package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean

/**
 * Write capability over the database: persists, deletes, merges, builds foreign-key references, and runs a statement
 * `RawSqlOutsideInventory` holds. Exposes nothing that reads a row, so a holder cannot root an unfiltered query through
 * it. The read capability (the [io.ebean.Database] type) is confined to this module's producer and the two port
 * implementations; see ADR 0008.
 */
interface Persistor {
    fun save(bean: Any)

    fun saveAll(beans: Collection<Any>)

    fun delete(bean: Any)

    fun merge(bean: Any)

    fun <T : Any> reference(type: Class<T>, id: Any): T

    /** Runs [statement] with its positional [parameters] and answers the number of rows it wrote. */
    fun executeRaw(statement: String, vararg parameters: Any?): Int
}
