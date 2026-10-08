package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID.randomUUID

/** A person as a caller names it, by its name and its addresses, before it is found or created. */
data class PersonReference(val name: String, val urls: List<String>)

@ApplicationScoped
class PersonCreator(
    private val personRepository: PersonRepositoryInterface,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    /** One transaction around the read and the write, for the reason TagCreator.resolve gives. */
    fun findOrCreate(
        name: String,
        urls: List<String>,
        user: User,
    ): Person = transactionRunner.inTransaction {
        personRepository.findUserPerson(user = user, name = name, urls = urls)
            ?: personRepository.savePerson(
                Person(id = randomUUID(), author = user, name = name, urls = urls, createdAt = clock.now())
            )
    }
}
