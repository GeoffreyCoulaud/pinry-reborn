package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import java.util.UUID

interface PersonRepositoryInterface {
    fun savePerson(person: Person): Person

    /** The user's person of this name, folded on ASCII case, holding these addresses. */
    fun findUserPerson(
        user: User,
        name: PersonName,
        urls: Set<HttpUrl>,
    ): Person?

    /**
     * At most [limit] of the user's persons whose name holds [query], those beginning with it first, ASCII case folded.
     */
    fun findPersonsForUserMatching(
        user: User,
        query: String,
        limit: Int,
    ): List<Person>

    /** The persons of these identifiers, which the user data import found or created. */
    fun findPersonsByIds(ids: Set<UUID>): List<Person>

    /** Every person of the user, which the user data export lists. */
    fun findAllPersonsForUser(user: User): List<Person>

    /** Called after the user's pins are deleted, which are what reference a person. */
    fun deleteAllPersonsForUser(user: User)
}
