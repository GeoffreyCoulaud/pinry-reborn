package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PinModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPersonModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinCreatorModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinTagModel

/** Queries rooted on pins. */
object PinQueries : SoftDeletableQueries<PinModel, QPinModel>(::QPinModel, { it.softDeletedAt })

/** The pin a join row names, selected as the foreign key rather than joined back. */
private const val PIN_ID_PATH = "pin.id"

/**
 * Pins whose description, or a tag's or credited person's name, contains [query]; a null [query] filters nothing. Names
 * are read through subqueries scoped to [reader]; the junction is closed so the cursor's clauses stay outside.
 */
fun QPinModel.matchingText(reader: User, query: String?): QPinModel =
    if (query == null) {
        this
    } else {
        val tagged = QPinTagModel().tag.author.id.equalTo(reader.id).tag.name.contains(query)
        val credited = QPinCreatorModel().person.author.id.equalTo(reader.id).person.name.contains(query)
        val publishers = QPersonModel().author.id.equalTo(reader.id).name.contains(query)
        or()
            .description
            .contains(query)
            .id
            .isIn(tagged.select(PIN_ID_PATH).query())
            .id
            .isIn(credited.select(PIN_ID_PATH).query())
            .publisher
            .id
            .isIn(publishers.select("id").query())
            .endOr()
    }
