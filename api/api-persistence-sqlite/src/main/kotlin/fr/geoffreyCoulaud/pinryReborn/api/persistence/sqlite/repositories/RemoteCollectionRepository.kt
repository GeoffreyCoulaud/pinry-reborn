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

@ApplicationScoped
class RemoteCollectionRepository(persistor: Persistor) : RemoteCollectionRepositoryInterface {
    private val sqlRepository = ModelRepository<RemoteCollectionModel>(persistor = persistor)

    override fun saveRemoteCollection(collection: RemoteCollection): RemoteCollection =
        sqlRepository.saveAndReturn(collection.toModel()).toDomain()

    override fun findUserRemoteCollectionByUrl(user: User, url: String): RemoteCollection? =
        QRemoteCollectionModel().author.id.equalTo(user.id).url.equalTo(url).findOne()?.toDomain()
}
