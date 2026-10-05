package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.queries.PinQueries
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

@ApplicationScoped
class EbeanPinDuplicateRepository(
    private val persistor: Persistor,
) : PinDuplicateRepositoryInterface {
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
        return QPinDuplicateModel().or().firstPinId.notIn(pins).secondPinId.notIn(pins).endOr().delete()
    }

    override fun findShownFor(pinId: UUID): Map<UUID, Boolean> =
        pairsOf(pinId).shown().findList().associate { pair ->
            val other = if (pair.firstPinId == pinId) pair.secondPinId else pair.firstPinId
            other to (pair.rejectedAt != null)
        }

    override fun findPinIdsWithPending(pinIds: Collection<UUID>): Set<UUID> {
        val asked = pinIds.toSet()
        return QPinDuplicateModel().or().firstPinId.isIn(asked).secondPinId.isIn(asked).endOr()
            .rejectedAt.isNull.shown().findList()
            .flatMap { listOf(it.firstPinId, it.secondPinId) }
            .filter { it in asked }
            .toSet()
    }

    override fun setRejected(pinId: UUID, otherPinId: UUID, rejectedAt: Instant?): Boolean {
        val (first, second) = ordered(pinId, otherPinId)
        val pair = QPinDuplicateModel().firstPinId.equalTo(first).secondPinId.equalTo(second).shown()
        return pair.asUpdate().set("rejectedAt", rejectedAt).update() > 0
    }

    private fun pairsOf(pinId: UUID) =
        QPinDuplicateModel().or().firstPinId.equalTo(pinId).secondPinId.equalTo(pinId).endOr()

    // A pair whose pins are both active; one recycled hides it (ADR 0051, decision 7).
    private fun QPinDuplicateModel.shown(): QPinDuplicateModel =
        firstPinId.isIn(activePinIds()).secondPinId.isIn(activePinIds())

    private fun activePinIds() = PinQueries.active().select("id").query()

    private fun ordered(pinId: UUID, otherPinId: UUID) = listOf(pinId, otherPinId).sortedBy { it.toString() }
}
