package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.canonicalUrls
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PersonModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPersonModel
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PersonRepository(persistor: Persistor) : PersonRepositoryInterface {
    private val sqlRepository = ModelRepository<PersonModel>(persistor = persistor)

    override fun savePerson(person: Person): Person = sqlRepository.saveAndReturn(person.toModel()).toDomain()

    // `collate nocase` folds ASCII alone, as the unique index does, where Ebean's `ieq` would fold Unicode.
    override fun findUserPerson(
        user: User,
        name: String,
        urls: Collection<String>,
    ): Person? =
        QPersonModel()
            .author
            .id
            .equalTo(user.id)
            .raw("name collate nocase = ?", name)
            .urls
            .equalTo(canonicalUrls(urls))
            .findOne()
            ?.toDomain()

    // The names starting with the query first, then those containing it, up to what is left of the limit.
    override fun findPersonsForUserMatching(
        user: User,
        query: String,
        limit: Int,
    ): List<Person> {
        if (limit <= 0) return emptyList()

        val prefixed = QPersonModel().author.id.equalTo(user.id).name.startsWith(query).setMaxRows(limit).findList()
        val remaining = limit - prefixed.size
        val contained =
            if (remaining == 0) {
                emptyList()
            } else {
                QPersonModel()
                    .author
                    .id
                    .equalTo(user.id)
                    .name
                    .contains(query)
                    .not()
                    .name
                    .startsWith(query)
                    .endNot()
                    .setMaxRows(remaining)
                    .findList()
            }
        return (prefixed + contained).map { it.toDomain() }
    }

    override fun deleteAllPersonsForUser(user: User) {
        QPersonModel().author.id.equalTo(user.id).delete()
    }
}
