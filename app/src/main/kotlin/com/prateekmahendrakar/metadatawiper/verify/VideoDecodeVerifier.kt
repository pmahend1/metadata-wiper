package com.prateekmahendrakar.metadatawiper.verify

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.abs

/*
 * Proves a stripped video still demuxes to the same tracks and still gives a frame. It decodes
 * one sync frame rather than the whole stream, which stays cheap even for 4K. Removed tracks
 * are timed metadata, which the platform hides or exposes with an application MIME type, so
 * only video and audio tracks are compared.
 */
class VideoDecodeVerifier {

    // region State

    private companion object {
        const val DURATION_TOLERANCE_US = 1_000L

        // Only for a track that doesn't state its largest sample; big enough for a 4K keyframe.
        const val FALLBACK_SAMPLE_BUFFER_SIZE = 8 * 1024 * 1024
    }

    // endregion State

    // region Methods

    fun verify(original: SeekableByteChannel,
               cleaned: File): DecodeCheck {
        val originalTracks = originalTracks(original)
        if (originalTracks.isNullOrEmpty()) {
            return DecodeCheck.Skipped
        }

        val isCleanedReadable = try {
            withExtractor({ it.setDataSource(cleaned.path) }) { extractor ->
                val cleanedTracks = tracksOf(extractor)
                matches(originalTracks, cleanedTracks) && readsFirstSamples(extractor, cleanedTracks)
            }
        } catch (_: IOException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
        if (isCleanedReadable == false) {
            return DecodeCheck.Failed
        }

        if (originalTracks.none { it.isVideo }) {
            return DecodeCheck.Passed
        }
        if (hasFrame { it.setDataSource(cleaned.path) }) {
            return DecodeCheck.Passed
        }

        // A codec this device can't decode gives no frame from either file, which proves nothing.
        return if (hasFrame { it.setDataSource(ChannelMediaDataSource(original)) }) DecodeCheck.Failed else DecodeCheck.Skipped
    }

    // Null when the platform can't demux the original, so the cleaned copy can't be held to it.
    private fun originalTracks(original: SeekableByteChannel): List<VideoTrack>? {
        return try {
            withExtractor({ it.setDataSource(ChannelMediaDataSource(original)) }) { tracksOf(it) }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun <T> withExtractor(open: (MediaExtractor) -> Unit,
                                  block: (MediaExtractor) -> T): T {
        val extractor = MediaExtractor()
        try {
            open(extractor)
            return block(extractor)
        } finally {
            extractor.release()
        }
    }

    private fun tracksOf(extractor: MediaExtractor): List<VideoTrack> {
        return (0 until extractor.trackCount).mapNotNull { index ->
            val format = extractor.getTrackFormat(index)
            val mimeType = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                VideoTrack(index = index,
                           mimeType = mimeType,
                           width = format.intOrNull(MediaFormat.KEY_WIDTH),
                           height = format.intOrNull(MediaFormat.KEY_HEIGHT),
                           sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
                           channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
                           durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else null,
                           maxSampleSize = format.intOrNull(MediaFormat.KEY_MAX_INPUT_SIZE))
            } else {
                null
            }
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private fun matches(originals: List<VideoTrack>,
                        cleaned: List<VideoTrack>): Boolean {
        if (originals.size != cleaned.size) {
            return false
        }
        return originals.zip(cleaned).all { (original, kept) -> isSameTrack(original, kept) }
    }

    private fun isSameTrack(original: VideoTrack,
                            kept: VideoTrack): Boolean {
        val sameShape = original.mimeType == kept.mimeType
                        && original.width == kept.width
                        && original.height == kept.height
                        && original.sampleRate == kept.sampleRate
                        && original.channelCount == kept.channelCount
        val originalDuration = original.durationUs
        val keptDuration = kept.durationUs
        if (originalDuration == null || keptDuration == null) {
            return sameShape && originalDuration == keptDuration
        }
        return sameShape && abs(originalDuration - keptDuration) <= DURATION_TOLERANCE_US
    }

    private fun readsFirstSamples(extractor: MediaExtractor,
                                  tracks: List<VideoTrack>): Boolean {
        return tracks.all { track ->
            extractor.selectTrack(track.index)
            val buffer = ByteBuffer.allocate(track.maxSampleSize ?: FALLBACK_SAMPLE_BUFFER_SIZE)
            val sampleSize = extractor.readSampleData(buffer, 0)
            extractor.unselectTrack(track.index)
            sampleSize >= 0
        }
    }

    private fun hasFrame(open: (MediaMetadataRetriever) -> Unit): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            open(retriever)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            frame?.recycle()
            frame != null
        } catch (_: RuntimeException) {
            false
        } finally {
            retriever.release()
        }
    }

    // endregion Methods
}
