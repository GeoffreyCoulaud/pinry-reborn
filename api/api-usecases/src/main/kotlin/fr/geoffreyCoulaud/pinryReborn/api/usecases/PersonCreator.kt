package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonUrls
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TransactionRunner
import fr.geoffreyCoulaud.pinryReborn.api.domain.time.Clock
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID.randomUUID

@ApplicationScoped
class PersonCreator(
    private val personRepository: PersonRepositoryInterface,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    /** One transaction around the read and the write, for the reason TagCreator.resolve gives. */
    fun findOrCreate(
        name: String,
        urls: Collection<String>,
        user: User,
    ): Person {
        val canonicalUrls = PersonUrls.of(urls)
        return transactionRunner.inTransaction {
            personRepository.findUserPerson(user = user, name = name, urls = canonicalUrls)
                ?: personRepository.savePerson(
                    Person(id = randomUUID(), author = user, name = name, urls = canonicalUrls, createdAt = clock.now())
                )
        }
    }
}
