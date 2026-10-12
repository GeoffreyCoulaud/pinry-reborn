package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.TransactionControl
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class EbeanTransactionRunner(private val transactionControl: TransactionControl) : TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T =
        transactionControl.beginTransaction().use { transaction ->
            val result = block()
            transaction.commit()
            result
        }
}
