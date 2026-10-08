package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.RemoteCollection
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.RemoteCollectionRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.RemoteCollectionModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.RemoteCollectionModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.RemoteCollectionModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QRemoteCollectionModel
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class RemoteCollectionRepository(persistor: Persistor) : RemoteCollectionRepositoryInterface {
    private val sqlRepository = ModelRepository<RemoteCollectionModel>(persistor = persistor)

    override fun saveRemoteCollection(collection: RemoteCollection): RemoteCollection =
        sqlRepository.saveAndReturn(collection.toModel()).toDomain()

    override fun findUserRemoteCollectionByUrl(user: User, url: String): RemoteCollection? =
        QRemoteCollectionModel().author.id.equalTo(user.id).url.equalTo(url).findOne()?.toDomain()

    override fun findRemoteCollectionsForBoard(boardId: UUID): List<RemoteCollection> =
        QRemoteCollectionModel()
            .board
            .id
            .equalTo(boardId)
            .findList()
            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
            .map { it.toDomain() }

    override fun findAllRemoteCollectionsForUser(user: User): List<RemoteCollection> =
        QRemoteCollectionModel().author.id.equalTo(user.id).findList().map { it.toDomain() }
}
