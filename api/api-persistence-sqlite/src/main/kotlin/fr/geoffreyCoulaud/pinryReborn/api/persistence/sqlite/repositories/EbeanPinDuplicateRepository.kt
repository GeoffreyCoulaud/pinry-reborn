package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinDuplicateRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.PinDuplicateModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinDuplicateModel
import jakarta.enterprise.context.ApplicationScoped
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
            val (first, second) = listOf(pinId, other).sortedBy { it.toString() }
            persistor.save(PinDuplicateModel(randomUUID(), first, second, rejectedAt = null))
        }
    }

    private fun pairsOf(pinId: UUID) =
        QPinDuplicateModel().or().firstPinId.equalTo(pinId).secondPinId.equalTo(pinId).endOr()
}
