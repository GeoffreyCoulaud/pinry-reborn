package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QImageModel

// An image is not itself recyclable, so it has no state to ask about until its pin is navigated: an
// extension, like the download row's, is how that reaches the queries package.

/** Images whose pin is in any state. The caller states that it means it. */
fun QImageModel.withPinInAnyState(): QImageModel = this
