package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonUrls
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PersonModel

object PersonModelMapper {
    fun Person.toModel() =
        PersonModel(
            id = id,
            author = author.toModel(),
            name = name,
            urls = urls.joined,
            createdAt = createdAt,
        )

    fun PersonModel.toDomain() =
        Person(
            id = id,
            author = author.toDomain(),
            name = name,
            urls = PersonUrls.parse(urls),
            createdAt = createdAt,
        )
}
