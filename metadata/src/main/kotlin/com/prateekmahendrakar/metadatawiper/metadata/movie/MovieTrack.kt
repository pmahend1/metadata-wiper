package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.SampleTable

/*
 * One `trak`, as much of it as deciding what to do with the track takes. [chapterTrackIds] are
 * the tracks this one names in `tref/chap`. Those are ordinary text tracks, so only another
 * track's reference tells them apart from subtitles.
 */
class MovieTrack(val box: Box,
                 val id: Long,
                 val handlerType: String,
                 val sampleEntries: List<Box>,
                 val chapterTrackIds: Set<Long>,
                 val sampleTable: SampleTable) {

    // region State

    private companion object {
        val timedMetadataHandlers = setOf("meta", "camm")
        val timedMetadataFormats = setOf("gpmd", "camm", "mebx", "rtmd", "djmd", "mett", "metx", "urim", "fdsc")
        val textHandlers = setOf("sbtl", "text", "subt", "clcp")

        const val VIDEO = "vide"
        const val AUDIO = "soun"
        const val TIMECODE = "tmcd"
        const val HINT = "hint"
    }

    // endregion State

    // region Methods

    // The sample format goes first: telemetry is what matters, and some writers give it a generic handler.
    fun kind(chapterTargets: Set<Long>): TrackKind {
        val formats = sampleEntries.map { it.type }
        return when {
            handlerType in timedMetadataHandlers || formats.any { it in timedMetadataFormats } -> TrackKind.TimedMetadata
            handlerType == TIMECODE || TIMECODE in formats -> TrackKind.Timecode
            handlerType == VIDEO -> TrackKind.Video
            handlerType == AUDIO -> TrackKind.Audio
            handlerType in textHandlers -> if (id in chapterTargets) TrackKind.Chapter else TrackKind.Subtitle
            handlerType == HINT -> TrackKind.Hint
            else -> TrackKind.Unknown
        }
    }

    // endregion Methods
}
