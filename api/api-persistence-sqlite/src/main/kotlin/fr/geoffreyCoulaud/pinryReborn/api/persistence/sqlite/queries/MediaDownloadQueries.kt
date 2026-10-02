package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaDownloadModel

// A download row is not itself recyclable, so it has no state to ask about until its pin is
// navigated: an extension, like the pin-to-board join's, is how that reaches the queries package.

/** Downloads whose pin is not in the recycle bin. */
fun QMediaDownloadModel.withActivePin(): QMediaDownloadModel = pin.softDeletedAt.isNull
