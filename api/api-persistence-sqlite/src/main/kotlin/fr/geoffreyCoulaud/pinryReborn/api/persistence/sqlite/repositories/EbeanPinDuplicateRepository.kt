package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.PinQueries
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.withActivePins
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

@ApplicationScoped
class EbeanPinDuplicateRepository(private val persistor: Persistor) : PinDuplicateRepositoryInterface {
    override fun deletePending(pinId: UUID) {
        pairsOf(pinId).rejectedAt.isNull.delete()
    }

    override fun addMissing(pinId: UUID, otherPinIds: Collection<UUID>) {
        val held = pairsOf(pinId).findList().flatMap { listOf(it.firstPinId, it.secondPinId) }.toSet()
        (otherPinIds.toSet() - held).forEach { other ->
            val (first, second) = ordered(pinId, other)
            persistor.save(PinDuplicateModel(randomUUID(), first, second, rejectedAt = null))
        }
    }

    override fun deleteOrphans(): Int {
        val pins = PinQueries.any().select("id").query()
        val gone = QPinDuplicateModel().or().firstPinId.notIn(pins).secondPinId.notIn(pins).endOr().delete()
        // What a crash between `DeletePinMedia`'s two deletes would leave: a pin with no media is never hashed again.
        val withMedia = QMediaModel().select("pinId").query()
        return gone +
            QPinDuplicateModel()
                .rejectedAt
                .isNull
                .or()
                .firstPinId
                .notIn(withMedia)
                .secondPinId
                .notIn(withMedia)
                .endOr()
                .delete()
    }

    override fun findShownFor(pinId: UUID): Map<UUID, Boolean> =
        pairsOf(pinId).withActivePins().findList().associate { pair ->
            val other = if (pair.firstPinId == pinId) pair.secondPinId else pair.firstPinId
            other to (pair.rejectedAt != null)
        }

    override fun findPinIdsWithPending(pinIds: Collection<UUID>): Set<UUID> {
        val asked = pinIds.toSet()
        return QPinDuplicateModel()
            .or()
            .firstPinId
            .isIn(asked)
            .secondPinId
            .isIn(asked)
            .endOr()
            .rejectedAt
            .isNull
            .withActivePins()
            .findList()
            .flatMap { listOf(it.firstPinId, it.secondPinId) }
            .filter { it in asked }
            .toSet()
    }

    override fun setRejected(pinId: UUID, otherPinIds: Collection<UUID>, rejectedAt: Instant) {
        val others = otherPinIds.toSet()
        val pairs = pairsOf(pinId).or().firstPinId.isIn(others).secondPinId.isIn(others).endOr().withActivePins()
        pairs.asUpdate().set("rejectedAt", rejectedAt).update()
    }

    private fun pairsOf(pinId: UUID) =
        QPinDuplicateModel().or().firstPinId.equalTo(pinId).secondPinId.equalTo(pinId).endOr()

    private fun ordered(pinId: UUID, otherPinId: UUID) = listOf(pinId, otherPinId).sortedBy { it.toString() }
}
