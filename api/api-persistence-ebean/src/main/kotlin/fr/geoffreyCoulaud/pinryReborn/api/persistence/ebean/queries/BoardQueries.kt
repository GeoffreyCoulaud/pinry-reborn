package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.queries

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.BoardModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QBoardModel

/** Queries rooted on boards. */
object BoardQueries : SoftDeletableQueries<BoardModel, QBoardModel>(::QBoardModel, { it.softDeletedAt })

/** By name under the column's collation, the id breaking a tie. */
fun QBoardModel.orderedByName(): QBoardModel = orderBy().name.asc().id.asc()
