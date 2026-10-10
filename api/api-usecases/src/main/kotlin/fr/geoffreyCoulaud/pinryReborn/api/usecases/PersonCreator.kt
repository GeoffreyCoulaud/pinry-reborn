package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID.randomUUID

/** A person as a caller names it, by its name and its addresses, before it is found or created. */
data class PersonReference(val name: PersonName, val urls: Set<HttpUrl>)

@ApplicationScoped
class PersonCreator(
    private val personRepository: PersonRepositoryInterface,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    /** A person this instance invents, stamped from the clock as every use case stamps what it invents. */
    // An overload, not a default: a default reading `clock` runs on the CDI client proxy, whose field is null.
    fun findOrCreate(
        name: PersonName,
        urls: Set<HttpUrl>,
        user: User,
    ): Person = findOrCreate(name = name, urls = urls, user = user, createdAt = clock.now())

    /** The read and the write in one transaction, so a concurrent creation converges on one row. */
    fun findOrCreate(
        name: PersonName,
        urls: Set<HttpUrl>,
        user: User,
        // Not the clock: the user data import stamps with its own instant (specification 2026-10-08, decision D).
        createdAt: Instant,
    ): Person = transactionRunner.inTransaction {
        personRepository.findUserPerson(user = user, name = name, urls = urls)
            ?: personRepository.savePerson(
                Person(id = randomUUID(), author = user, name = name, urls = urls, createdAt = createdAt)
            )
    }
}
