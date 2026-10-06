package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Media
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.MediaDownload
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadReason
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus

enum class PinMediaStatus {
    NONE,
    PENDING,
    READY,
    FAILED,
}

data class PinMediaReplacement(val status: DownloadStatus, val reasonCode: DownloadReason?)

data class PinMediaState(
    val status: PinMediaStatus,
    val media: Media?,
    val reasonCode: DownloadReason?,
    val replacement: PinMediaReplacement?,
) {
    companion object {
        fun derive(media: Media?, download: MediaDownload?): PinMediaState =
            if (media != null) {
                val replacement = download?.let { PinMediaReplacement(it.status, it.reasonCode) }
                PinMediaState(PinMediaStatus.READY, media, null, replacement)
            } else if (download == null) {
                PinMediaState(PinMediaStatus.NONE, null, null, null)
            } else if (download.status == DownloadStatus.PENDING) {
                PinMediaState(PinMediaStatus.PENDING, null, null, null)
            } else {
                PinMediaState(PinMediaStatus.FAILED, null, download.reasonCode, null)
            }
    }
}
