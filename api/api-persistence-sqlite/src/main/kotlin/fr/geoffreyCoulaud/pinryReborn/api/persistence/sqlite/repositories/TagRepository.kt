package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Tag
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.TagRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.Persistor
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.TagModelMapper.toDomain
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.mappers.TagModelMapper.toModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.TagModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QPinTagModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QTagModel
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class TagRepository(
    persistor: Persistor,
) : TagRepositoryInterface {
    private val sqlRepository = ModelRepository<TagModel>(persistor = persistor)

    override fun saveTag(tag: Tag): Tag = sqlRepository.saveAndReturn(tag.toModel()).toDomain()

    // The comparison goes through the column's own collation rather than Ebean's `ieq`, which
    // renders lower(column) = ? with the bind lowercased in Java: that fold is Unicode aware while
    // `collate nocase` is ASCII only, so the read and ix_tags_author_name_nocase would disagree in
    // one direction and find-or-create would depend on which case was stored first.
    override fun findUserTagByName(
        user: User,
        name: String,
    ): Tag? =
        QTagModel()
            .author.id
            .equalTo(user.id)
            .raw("name collate nocase = ?", name)
            .findOne()
            ?.toDomain()

    override fun findAllTagsForUser(user: User): List<Tag> =
        QTagModel()
            .author.id
            .equalTo(user.id)
            .findList()
            .map { it.toDomain() }

    // `contains` and `startsWith` rather than their case-insensitive twins: a bare LIKE folds A to Z
    // as ix_tags_author_name_nocase does, where `icontains` lowercases the bind in Java and would
    // disagree with the index on a name outside ASCII, as findUserTagByName's comment records.
    override fun findTagsForUserMatching(
        user: User,
        query: String,
        limit: Int,
    ): List<Tag> {
        // setMaxRows reads a non-positive bound as no bound, so the refusal is here where every caller passes.
        if (limit <= 0) return emptyList()

        val prefixed = QTagModel()
            .author.id
            .equalTo(user.id)
            .name
            .startsWith(query)
            .setMaxRows(limit)
            .findList()
        val remaining = limit - prefixed.size
        if (remaining == 0) return prefixed.map { it.toDomain() }

        val contained = QTagModel()
            .author.id
            .equalTo(user.id)
            .name
            .contains(query)
            .not()
            .name
            .startsWith(query)
            .endNot()
            .setMaxRows(remaining)
            .findList()
        return (prefixed + contained).map { it.toDomain() }
    }

    override fun deleteAllTagsForUser(user: User) {
        val tagIds = QTagModel().author.id.equalTo(user.id).findList().map { it.id }
        if (tagIds.isEmpty()) return
        // Remove pin_tag junction rows first (FK order), mirroring PinRepository's
        // permanentlyDeleteAllPinsForUser / permanentlyDeleteAllBoardsForUser defensive style,
        // so this method is self-sufficient regardless of call order.
        QPinTagModel().tag.id.isIn(tagIds).delete()
        QTagModel().id.isIn(tagIds).delete()
    }
}
