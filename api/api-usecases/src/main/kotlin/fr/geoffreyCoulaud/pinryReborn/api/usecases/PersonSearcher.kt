package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.SearchEmptyQueryError
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PersonSearcher(private val personRepository: PersonRepositoryInterface) {
    /** As [TagSearcher.searchTags], over the user's persons: a blank query throws [SearchEmptyQueryError]. */
    fun searchPersons(
        user: User,
        query: String,
        limit: Int,
    ): List<Person> {
        if (query.isBlank()) {
            throw SearchEmptyQueryError()
        }

        return personRepository.findPersonsForUserMatching(user = user, query = query, limit = limit)
    }
}
