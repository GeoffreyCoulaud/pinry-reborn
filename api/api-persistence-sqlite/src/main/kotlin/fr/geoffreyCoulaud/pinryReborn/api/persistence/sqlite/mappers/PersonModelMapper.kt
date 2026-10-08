package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PersonModel
import io.ebean.text.json.EJson

object PersonModelMapper {
    /** The stored form the unique index compares: a JSON array, sorted and distinct, so order and repeats vanish. */
    fun canonicalUrls(urls: Collection<String>): String = EJson.write(urls.toSortedSet().toList())

    fun Person.toModel() =
        PersonModel(
            id = id,
            author = author.toModel(),
            name = name,
            urls = canonicalUrls(urls),
            createdAt = createdAt,
        )

    fun PersonModel.toDomain() =
        Person(
            id = id,
            author = author.toDomain(),
            name = name,
            urls = EJson.parseList(urls).map { it.toString() },
            createdAt = createdAt,
        )
}
