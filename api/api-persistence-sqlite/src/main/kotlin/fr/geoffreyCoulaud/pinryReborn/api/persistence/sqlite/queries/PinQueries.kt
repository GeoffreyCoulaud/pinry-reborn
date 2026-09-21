package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PinModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinTagModel

/** Queries rooted on pins. */
object PinQueries : SoftDeletableQueries<PinModel, QPinModel>(::QPinModel, { it.softDeletedAt })

/** The pin a join row names, selected as the foreign key rather than joined back. */
private const val PIN_ID_PATH = "pin.id"

/**
 * Pins whose description or tag name contains [query]; a null [query] filters nothing. The tag half
 * is a subquery scoped to [reader], and its junction is closed so the cursor's clauses stay outside it.
 */
fun QPinModel.matchingText(reader: User, query: String?): QPinModel =
    if (query == null) {
        this
    } else {
        val tagged = QPinTagModel().tag.author.id.equalTo(reader.id).tag.name.contains(query)
        or()
            .description.contains(query)
            .id.isIn(tagged.select(PIN_ID_PATH).query())
            .endOr()
    }
