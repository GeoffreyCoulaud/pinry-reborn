package fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.TagModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.mappers.TagModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.TagModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.ebean.models.query.QTagModel
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class TagRepository(persistor: Persistor) : TagRepositoryInterface {
    private val sqlRepository = ModelRepository<TagModel>(persistor = persistor)

    override fun saveTag(tag: Tag): Tag = sqlRepository.saveAndReturn(tag.toModel()).toDomain()

    // A plain equality, the column's collation folding as ux_tags_author_name does: a fold of
    // its own would let find-or-create depend on which case was stored first.
    override fun findUserTagByName(
        user: User,
        name: String,
    ): Tag? = QTagModel().author.id.equalTo(user.id).name.eq(name).findOne()?.toDomain()

    override fun findAllTagsForUser(user: User): List<Tag> =
        QTagModel().author.id.equalTo(user.id).findList().map { it.toDomain() }

    // `contains` and `startsWith` rather than their case-insensitive twins: LIKE folds under the
    // column's collation as ux_tags_author_name does, and ILIKE refuses that collation.
    override fun findTagsForUserMatching(
        user: User,
        query: String,
        limit: Int,
    ): List<Tag> {
        // setMaxRows reads a non-positive bound as no bound, so the refusal is here where every caller passes.
        if (limit <= 0) return emptyList()

        val prefixed = QTagModel().author.id.equalTo(user.id).name.startsWith(query).setMaxRows(limit).findList()
        val remaining = limit - prefixed.size
        val contained =
            if (remaining == 0) {
                emptyList()
            } else {
                QTagModel()
                    .author
                    .id
                    .equalTo(user.id)
                    .name
                    .contains(query)
                    .not()
                    .name
                    .startsWith(query)
                    .endNot()
                    .setMaxRows(remaining)
                    .findList()
            }
        return (prefixed + contained).map { it.toDomain() }
    }
}
