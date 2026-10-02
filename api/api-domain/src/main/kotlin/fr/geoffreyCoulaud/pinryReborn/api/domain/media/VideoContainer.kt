package fr.geoffreyCoulaud.pinryReborn.api.domain.media

/** The container a video is stored in, which follows its kept tracks' codecs (ADR 0047, decision 2). */
enum class VideoContainer {
    MP4,
    WEBM,
    ;

    companion object {
        fun of(videoCodec: VideoCodec, audioCodec: AudioCodec?): VideoContainer {
            val videoFits = videoCodec == VideoCodec.VP9 || videoCodec == VideoCodec.AV1
            val audioFits = audioCodec == null || audioCodec == AudioCodec.OPUS
            return if (videoFits && audioFits) WEBM else MP4
        }
    }
}
