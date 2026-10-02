package fr.geoffreyCoulaud.pinryReborn.api.domain.media

/** Base for failures raised while reading or writing a video with [VideoProcessor]. */
sealed class VideoProcessorException(message: String) : Exception(message)

/** A track's codec is not one ADR 0047 accepts; the message names it. */
class VideoCodecUnsupportedException(message: String) : VideoProcessorException(message)

class VideoTooLongException(message: String) : VideoProcessorException(message)

/** Not a video the processor reads: refused by its demuxers, no video track, no duration, or a single frame. */
class UndecodableVideoException(message: String) : VideoProcessorException(message)

/** The process ran past its timeout and was destroyed. */
class VideoProcessorTimeoutException(message: String) : VideoProcessorException(message)
