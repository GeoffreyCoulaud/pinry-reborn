package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonUrls
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PersonRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.PersonModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PersonModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPersonModel
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class PersonRepository(persistor: Persistor) : PersonRepositoryInterface {
    private val sqlRepository = ModelRepository<PersonModel>(persistor = persistor)

    override fun savePerson(person: Person): Person = sqlRepository.saveAndReturn(person.toModel()).toDomain()

    // The name through the column's collation, as TagRepository.findUserTagByName says why.
    override fun findUserPerson(
        user: User,
        name: String,
        urls: PersonUrls,
    ): Person? =
        QPersonModel()
            .author
            .id
            .equalTo(user.id)
            .raw("name collate nocase = ?", name)
            .urls
            .equalTo(urls.joined)
            .findOne()
            ?.toDomain()
}
