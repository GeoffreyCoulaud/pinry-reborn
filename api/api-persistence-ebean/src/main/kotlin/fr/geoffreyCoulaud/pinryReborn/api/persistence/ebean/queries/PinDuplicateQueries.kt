package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel

/** Pairs whose two pins are out of the recycle bin; one recycled hides the pair (ADR 0051, decision 7). */
fun QPinDuplicateModel.withActivePins(): QPinDuplicateModel =
    firstPin.softDeletedAt.isNull.secondPin.softDeletedAt.isNull
