package fr.geoffreyCoulaud.pinryReborn.api.domain.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User

interface PersonRepositoryInterface {
    fun savePerson(person: Person): Person

    /** The user's person of this name, folded on ASCII case, holding these addresses in any order. */
    fun findUserPerson(
        user: User,
        name: String,
        urls: Collection<String>,
    ): Person?
}
