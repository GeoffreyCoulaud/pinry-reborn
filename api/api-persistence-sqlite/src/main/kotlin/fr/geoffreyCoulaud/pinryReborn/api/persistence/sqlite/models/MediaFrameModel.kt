package fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.bases.BaseModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.repositories.FrameHashBands
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "media_frame")
// A media's frames, read and replaced together; `noConstraint` drops the index a foreign key would bring.
@Index(name = "ix_media_frame_media", columnNames = ["media_id"])
// One index per 16-bit band, which the lookup asks for its value and its sixteen one-bit neighbours.
@Index(
    name = "ix_media_frame_band_0",
    definition = "create index ix_media_frame_band_0 on media_frame (${FrameHashBands.BAND_0})",
)
@Index(
    name = "ix_media_frame_band_1",
    definition = "create index ix_media_frame_band_1 on media_frame (${FrameHashBands.BAND_1})",
)
@Index(
    name = "ix_media_frame_band_2",
    definition = "create index ix_media_frame_band_2 on media_frame (${FrameHashBands.BAND_2})",
)
@Index(
    name = "ix_media_frame_band_3",
    definition = "create index ix_media_frame_band_3 on media_frame (${FrameHashBands.BAND_3})",
)
@Index(
    name = "ix_media_frame_band_4",
    definition = "create index ix_media_frame_band_4 on media_frame (${FrameHashBands.BAND_4})",
)
@Index(
    name = "ix_media_frame_band_5",
    definition = "create index ix_media_frame_band_5 on media_frame (${FrameHashBands.BAND_5})",
)
@Index(
    name = "ix_media_frame_band_6",
    definition = "create index ix_media_frame_band_6 on media_frame (${FrameHashBands.BAND_6})",
)
@Index(
    name = "ix_media_frame_band_7",
    definition = "create index ix_media_frame_band_7 on media_frame (${FrameHashBands.BAND_7})",
)
@Index(
    name = "ix_media_frame_band_8",
    definition = "create index ix_media_frame_band_8 on media_frame (${FrameHashBands.BAND_8})",
)
@Index(
    name = "ix_media_frame_band_9",
    definition = "create index ix_media_frame_band_9 on media_frame (${FrameHashBands.BAND_9})",
)
@Index(
    name = "ix_media_frame_band_10",
    definition = "create index ix_media_frame_band_10 on media_frame (${FrameHashBands.BAND_10})",
)
@Index(
    name = "ix_media_frame_band_11",
    definition = "create index ix_media_frame_band_11 on media_frame (${FrameHashBands.BAND_11})",
)
@Index(
    name = "ix_media_frame_band_12",
    definition = "create index ix_media_frame_band_12 on media_frame (${FrameHashBands.BAND_12})",
)
@Index(
    name = "ix_media_frame_band_13",
    definition = "create index ix_media_frame_band_13 on media_frame (${FrameHashBands.BAND_13})",
)
@Index(
    name = "ix_media_frame_band_14",
    definition = "create index ix_media_frame_band_14 on media_frame (${FrameHashBands.BAND_14})",
)
@Index(
    name = "ix_media_frame_band_15",
    definition = "create index ix_media_frame_band_15 on media_frame (${FrameHashBands.BAND_15})",
)
class MediaFrameModel(
    id: UUID,
    var mediaId: UUID,
    @Column(name = "hash_0") var hash0: Long,
    @Column(name = "hash_1") var hash1: Long,
    @Column(name = "hash_2") var hash2: Long,
    @Column(name = "hash_3") var hash3: Long,
) : BaseModel(id) {
    // No constraint: the orphan sweep deletes a gone media's frames, so a media's delete never waits on them.
    @ManyToOne
    @DbForeignKey(noConstraint = true)
    @JoinColumn(name = "media_id", insertable = false, updatable = false)
    lateinit var media: MediaModel
}
