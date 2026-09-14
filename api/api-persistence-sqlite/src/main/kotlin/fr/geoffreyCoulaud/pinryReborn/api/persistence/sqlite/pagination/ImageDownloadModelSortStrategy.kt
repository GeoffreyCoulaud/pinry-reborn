package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.pagination

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.ImageDownloadModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QImageDownloadModel

/**
 * The task centre's one order: most recently requested first, on the pair `(requestedAt, id)` and
 * not the instant alone, a page boundary inside a group sharing an instant stalling the cursor.
 */
class ImageDownloadModelSortStrategy : ModelSortStrategy<ImageDownloadModel, QImageDownloadModel>() {
    override fun filterCursorAndForwardNeighbors(
        cursor: ModelCursor<ImageDownloadModel>,
        query: QImageDownloadModel,
    ): QImageDownloadModel =
        query
            .or()
            .requestedAt.lessThan(cursor.pivot.requestedAt)
            .let {
                it
                    .and()
                    .requestedAt.equalTo(cursor.pivot.requestedAt)
                    .raw("id <= ?", cursor.pivot.id)
                    .endAnd()
            }.endOr()

    override fun filterCursorAndBackwardNeighbors(
        cursor: ModelCursor<ImageDownloadModel>,
        query: QImageDownloadModel,
    ): QImageDownloadModel =
        query
            .or()
            .requestedAt.greaterThan(cursor.pivot.requestedAt)
            .let {
                it
                    .and()
                    .requestedAt.equalTo(cursor.pivot.requestedAt)
                    .raw("id >= ?", cursor.pivot.id)
                    .endAnd()
            }.endOr()

    override fun sortCursorAndForwardNeighbors(query: QImageDownloadModel): QImageDownloadModel =
        query.orderBy().requestedAt.desc().id.desc()

    override fun sortCursorAndBackwardNeighbors(query: QImageDownloadModel): QImageDownloadModel =
        query.orderBy().requestedAt.asc().id.asc()
}
