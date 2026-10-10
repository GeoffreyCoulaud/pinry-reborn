package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonCreator

/** A person stored from texts as a test writes them, each of which must parse. */
fun PersonCreator.findOrCreate(name: String, urls: List<String>, user: User): Person =
    findOrCreate(checkNotNull(PersonName.parse(name)), urls.map { checkNotNull(HttpUrl.parse(it)) }.toSet(), user)
