package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.MediaDownloadModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaDownloadModel

/**
 * The task centre's one order: most recently requested first, on the pair `(requestedAt, id)` and not the instant
 * alone, a page boundary inside a group sharing an instant stalling the cursor.
 */
class MediaDownloadModelSortStrategy : ModelSortStrategy<MediaDownloadModel, QMediaDownloadModel>() {
    override fun filterCursorAndForwardNeighbors(
        cursor: ModelCursor<MediaDownloadModel>,
        query: QMediaDownloadModel,
    ): QMediaDownloadModel =
        query
            .or()
            .requestedAt
            .lessThan(cursor.pivot.requestedAt)
            .let {
                it.and().requestedAt.equalTo(cursor.pivot.requestedAt).raw("id <= ?", cursor.pivot.id).endAnd()
            }
            .endOr()

    override fun filterCursorAndBackwardNeighbors(
        cursor: ModelCursor<MediaDownloadModel>,
        query: QMediaDownloadModel,
    ): QMediaDownloadModel =
        query
            .or()
            .requestedAt
            .greaterThan(cursor.pivot.requestedAt)
            .let {
                it.and().requestedAt.equalTo(cursor.pivot.requestedAt).raw("id >= ?", cursor.pivot.id).endAnd()
            }
            .endOr()

    override fun sortCursorAndForwardNeighbors(query: QMediaDownloadModel): QMediaDownloadModel =
        query.orderBy().requestedAt.desc().id.desc()

    override fun sortCursorAndBackwardNeighbors(query: QMediaDownloadModel): QMediaDownloadModel =
        query.orderBy().requestedAt.asc().id.asc()
}
