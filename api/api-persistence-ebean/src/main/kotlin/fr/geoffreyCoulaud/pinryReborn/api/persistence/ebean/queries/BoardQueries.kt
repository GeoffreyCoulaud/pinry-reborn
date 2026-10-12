package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.queries

import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.BoardModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QBoardModel

/** Queries rooted on boards. */
object BoardQueries : SoftDeletableQueries<BoardModel, QBoardModel>(::QBoardModel, { it.softDeletedAt })
