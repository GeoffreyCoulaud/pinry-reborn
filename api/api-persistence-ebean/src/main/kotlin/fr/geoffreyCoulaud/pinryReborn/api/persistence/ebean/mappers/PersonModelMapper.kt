package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.HttpUrlModelMapper.toHttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.UserModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PersonModel
import io.ebean.text.json.EJson

object PersonModelMapper {
    /** The stored form the unique index compares: a JSON array of the addresses' text, sorted. */
    fun canonicalUrls(urls: Set<HttpUrl>): String = EJson.write(urls.map(HttpUrl::toString).sorted())

    fun Person.toModel() =
        PersonModel(
            id = id,
            author = author.toModel(),
            name = name.text,
            urls = canonicalUrls(urls),
            createdAt = createdAt,
        )

    fun PersonModel.toDomain() =
        Person(
            id = id,
            author = author.toDomain(),
            name = checkNotNull(PersonName.parse(name)) { "The stored person name is refused: $name" },
            urls = EJson.parseList(urls).map { it.toString().toHttpUrl() }.toSet(),
            createdAt = createdAt,
        )
}
