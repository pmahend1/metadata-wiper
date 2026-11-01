package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.FreedBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TopLevelBoxes
import java.time.Instant

/*
 * Works out what a movie file holds and which bytes a strip overwrites. Nothing moves, because
 * `stco` tables hold absolute offsets: metadata boxes become zeroed `free` boxes of the same
 * size, and timestamps and names are zeroed in place. The one exception is the end of the file,
 * where a trailer and removed boxes can be cut off (see outputLength).
 *
 * Each level of the box tree has an allowlist, and anything else is freed. Timed-metadata and
 * timecode tracks fail the file for now: their samples hold what we're here to remove, and
 * freeing the `trak` alone would leave them readable in `mdat`.
 */
class MovieStripPlanner(private val reader: BinaryReader) {

    // region State

    private val boxReader = BoxReader(reader)
    private val trackReader = MovieTrackReader(reader, boxReader)
    private val metadataReader = MovieMetadataReader(reader, boxReader)

    private val items = mutableListOf<MetadataItem>()
    private val warnings = linkedSetOf<MetadataWarning>()
    private val blanked = mutableListOf<BlankedRange>()
    private val removedTopLevel = mutableSetOf<Box>()
    private val unknownHandlers = linkedSetOf<String>()
    private var hasFragments = false
    private var timestampedTracks = 0
    private var trackTimestampBytes = 0L

    private val paddingTypes = setOf(FREE, SKIP, WIDE)

    private val keptTopLevelTypes = setOf(FTYP, MDAT, MOOV, MOOF, "mfra", "sidx", "ssix", "styp", "prft", "emsg", "pdin")

    private val keptMovieTypes = setOf(MVHD, TRAK, "mvex", "iods")

    private val keptTrackTypes = setOf(TKHD, MDIA, "tref", "edts", "tapt", "clip", "matt", "load", "imap", "txas", "trgr")

    private val keptMediaTypes = setOf(MDHD, HDLR, MINF, "elng")

    private val keptMediaInfoTypes = setOf(HDLR, STBL, "vmhd", "smhd", "hmhd", "nmhd", "sthd", "gmhd", "dinf")

    private val keptSampleTableTypes = setOf("stsd", "stts", "ctts", "cslg", "stss", "stps", "stsh", "stdp", "sdtp",
                                             "stsc", "stsz", "stz2", "stco", "co64", "padb", "sbgp", "sgpd", "subs",
                                             "saiz", "saio")

    private val keptFragmentTypes = setOf(TRAF, "mfhd")

    private val keptFragmentTrackTypes = setOf("tfhd", "tfdt", "trun", "sbgp", "sgpd", "subs", "saiz", "saio", "senc")

    private companion object {
        const val FTYP = "ftyp"
        const val MDAT = "mdat"
        const val MOOV = "moov"
        const val MOOF = "moof"
        const val TRAF = "traf"
        const val FREE = "free"
        const val SKIP = "skip"
        const val WIDE = "wide"
        const val UDTA = "udta"
        const val META = "meta"
        const val UUID = "uuid"
        const val MVHD = "mvhd"
        const val TRAK = "trak"
        const val TKHD = "tkhd"
        const val MDIA = "mdia"
        const val MDHD = "mdhd"
        const val HDLR = "hdlr"
        const val MINF = "minf"
        const val STBL = "stbl"
        const val CMOV = "cmov"
        const val PSSH = "pssh"

        const val XAVC_BRAND = "XAVC"
        const val PANASONIC_BOX = "PANA"
        const val SAMSUNG_SIGNATURE = "SEFT"
        const val SIGNATURE_SIZE = 4

        const val V0_TIME_SIZE = 4L
        const val V1_TIME_SIZE = 8L
        const val BRAND_SIZE = 4
        const val MINOR_VERSION_SIZE = 4L

        // After `hdlr`'s version and flags: component type, handler type, and 12 reserved bytes.
        const val HANDLER_NAME_OFFSET = 20L

        // Sample entry header (8 bytes) then the visual fields before compressorname, per ISO 14496-12.
        const val COMPRESSOR_NAME_OFFSET = 42L
        const val COMPRESSOR_NAME_SIZE = 32

        // Movie times count seconds from 1904; anything past year 9999 isn't a date worth showing.
        const val SECONDS_1904_TO_1970 = 2_082_844_800L
        const val MAX_EPOCH_SECONDS = 253_402_300_799L
    }

    // endregion State

    // region Methods

    fun plan(): MovieStripPlan {
        val topLevel = boxReader.readTopLevel()
        val movie = movieBox(topLevel.boxes)
        val movieChildren = boxReader.readChildren(movie)
        rejectUnreadableMovie(movieChildren)
        val tracks = movieChildren.filter { it.type == TRAK }.map { trackReader.read(it) }
        val chunks = tracks.flatMap { it.sampleTable.chunks }.filter { it.length > 0 }

        planInStreamWarning(topLevel.boxes)
        planTopLevel(topLevel.boxes, movieChildren, tracks)
        planTrailing(topLevel.trailing, chunks)
        addTrackSummaries()

        val outputLength = outputLength(topLevel, chunks)
        val blankedRanges = checkedBlanked(chunks)
        return MovieStripPlan(items.toList(), warnings.toList(), blankedRanges.filter { it.span.start < outputLength }, outputLength)
    }

    private fun movieBox(topLevel: List<Box>): Box {
        val movies = topLevel.filter { it.type == MOOV }
        if (movies.size != 1) {
            throw MalformedFileException("Movie file has ${movies.size} 'moov' boxes instead of one")
        }
        return movies.single()
    }

    private fun rejectUnreadableMovie(movieChildren: List<Box>) {
        if (movieChildren.any { it.type == CMOV }) {
            throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Compressed movie headers aren't supported")
        }
        if (movieChildren.any { it.type == PSSH }) {
            throw UnsupportedFileException(UnsupportedReason.Encrypted, "Encrypted movies can't be cleaned")
        }
    }

    /*
     * Sony XAVC and some Panasonic cameras write the date and GPS into the H.264 stream, which
     * is out of reach here. Warning on every file would be noise, so only these cameras get it.
     */
    private fun planInStreamWarning(topLevel: List<Box>) {
        val ftyp = topLevel.firstOrNull { it.type == FTYP }
        if (ftyp != null && XAVC_BRAND in brands(ftyp)) {
            warnings += MetadataWarning(MetadataWarningKind.InStreamMetadataNotChecked)
        }
    }

    private fun brands(ftyp: Box): List<String> {
        val brandBytes = ftyp.end - ftyp.dataStart
        val offsets = (0L until brandBytes - BRAND_SIZE + 1 step BRAND_SIZE.toLong()).filter { it != MINOR_VERSION_SIZE }
        return offsets.map {
            reader.seek(ftyp.dataStart + it)
            String(reader.readBytes(BRAND_SIZE), Charsets.ISO_8859_1)
        }
    }

    // region Boxes

    private fun planTopLevel(topLevel: List<Box>,
                             movieChildren: List<Box>,
                             tracks: List<MovieTrack>) {
        for (box in topLevel) {
            when (box.type) {
                MOOV -> planMovie(movieChildren, tracks)
                MOOF -> planFragment(box)
                in keptTopLevelTypes -> continue
                in paddingTypes -> planPadding(box)
                else -> {
                    remove(box)
                    removedTopLevel += box
                }
            }
        }
    }

    private fun planChildren(children: List<Box>,
                             keptTypes: Set<String>) {
        for (box in children) {
            when (box.type) {
                in keptTypes -> continue
                in paddingTypes -> planPadding(box)
                else -> remove(box)
            }
        }
    }

    // Editors often turn metadata into padding by renaming the box, leaving its contents behind.
    private fun planPadding(box: Box) {
        val range = BlankedRange(FileSpan(box.headerEnd, box.end))
        blanked += range
        if (range.isAlreadyBlank(reader) == false) {
            items += MetadataItem(MetadataCategory.Other, MetadataLabel.LEFTOVER_DATA, byteSize = range.span.length)
        }
    }

    private fun remove(box: Box) {
        items += describe(box)
        blanked += FreedBox.ranges(box)
    }

    private fun describe(box: Box): List<MetadataItem> {
        val size = box.end - box.start
        return when (box.type) {
            UDTA -> metadataReader.read(box).also { planPanasonicWarning(it) }
            META -> metadataReader.read(box)
            UUID -> if (metadataReader.isXmp(box)) {
                listOf(MetadataItem(MetadataCategory.Other, MetadataLabel.XMP, byteSize = size))
            } else {
                listOf(MetadataItem(MetadataCategory.Other, MetadataLabel.UNKNOWN_BOX, box.type, size))
            }
            else -> listOf(MetadataItem(MetadataCategory.Other, MetadataLabel.UNKNOWN_BOX, box.type, size))
        }
    }

    private fun planPanasonicWarning(userData: List<MetadataItem>) {
        if (userData.any { it.label == MetadataLabel.VENDOR_DATA && it.detail == PANASONIC_BOX }) {
            warnings += MetadataWarning(MetadataWarningKind.InStreamMetadataNotChecked)
        }
    }

    // endregion Boxes

    // region Movie

    private fun planMovie(movieChildren: List<Box>,
                          tracks: List<MovieTrack>) {
        movieChildren.filter { it.type == MVHD }.forEach { planMovieHeader(it) }

        val chapterTargets = tracks.flatMap { it.chapterTrackIds }.toSet()
        tracks.forEach { planTrack(it, chapterTargets) }
        planChildren(movieChildren, keptMovieTypes)
    }

    private fun planMovieHeader(mvhd: Box) {
        val times = timeSpans(mvhd)
        planMovieTime(times.first, MetadataLabel.CREATION_TIME)
        planMovieTime(times.second, MetadataLabel.MODIFICATION_TIME)
    }

    private fun planMovieTime(span: FileSpan,
                              label: String) {
        blanked += BlankedRange(span)
        val seconds = readTime(span)
        if (seconds != 0L) {
            items += MetadataItem(MetadataCategory.DateTime, label, formatTime(seconds), span.length)
        }
    }

    // Creation then modification time. They follow version and flags, and version 1 widens them to 64 bits.
    private fun timeSpans(box: Box): Pair<FileSpan, FileSpan> {
        val start = boxReader.fullBoxDataStart(box)
        reader.seek(box.dataStart)
        val size = when (val version = reader.readU8()) {
            0 -> V0_TIME_SIZE
            1 -> V1_TIME_SIZE
            else -> throw MalformedFileException("'${box.type}' box at offset ${box.start} has unknown version $version")
        }
        if (box.end - start < size * 2) {
            throw MalformedFileException("'${box.type}' box at offset ${box.start} is too short")
        }
        return FileSpan(start, start + size) to FileSpan(start + size, start + size * 2)
    }

    private fun readTime(span: FileSpan): Long {
        reader.seek(span.start)
        return if (span.length == V1_TIME_SIZE) reader.readU64() else reader.readU32()
    }

    private fun formatTime(seconds: Long): String {
        val epochSeconds = seconds - SECONDS_1904_TO_1970
        return if (epochSeconds > MAX_EPOCH_SECONDS) "" else Instant.ofEpochSecond(epochSeconds).toString()
    }

    private fun planFragment(moof: Box) {
        hasFragments = true
        val children = boxReader.readChildren(moof)
        children.filter { it.type == TRAF }.forEach { planChildren(boxReader.readChildren(it), keptFragmentTrackTypes) }
        planChildren(children, keptFragmentTypes)
    }

    // endregion Movie

    // region Tracks

    private fun planTrack(track: MovieTrack,
                          chapterTargets: Set<Long>) {
        val kind = track.kind(chapterTargets)
        when (kind) {
            TrackKind.TimedMetadata, TrackKind.Timecode -> {
                throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Track ${track.id} is a $kind track")
            }
            TrackKind.Subtitle -> warnings += MetadataWarning(MetadataWarningKind.SubtitleTracksKept)
            TrackKind.Chapter -> warnings += MetadataWarning(MetadataWarningKind.ChaptersKept)
            TrackKind.Unknown -> unknownHandlers += track.handlerType
            TrackKind.Video -> track.sampleEntries.forEach { planCompressorName(it) }
            TrackKind.Audio, TrackKind.Hint -> Unit
        }

        val children = boxReader.readChildren(track.box)
        val headers = children.filter { it.type == TKHD } +
                      children.filter { it.type == MDIA }.flatMap { boxReader.readChildren(it) }.filter { it.type == MDHD }
        val spans = headers.flatMap { timeSpans(it).toList() }
        planTrackTimes(spans)
        children.filter { it.type == MDIA }.forEach { planMedia(it) }
        planChildren(children, keptTrackTypes)
    }

    private fun planTrackTimes(spans: List<FileSpan>) {
        blanked += spans.map { BlankedRange(it) }
        val present = spans.filter { readTime(it) != 0L }
        if (present.isNotEmpty()) {
            timestampedTracks++
            trackTimestampBytes += present.sumOf { it.length }
        }
    }

    private fun planMedia(mdia: Box) {
        val children = boxReader.readChildren(mdia)
        children.filter { it.type == HDLR }.forEach { planHandlerName(it) }
        children.filter { it.type == MINF }.forEach { planMediaInfo(it) }
        planChildren(children, keptMediaTypes)
    }

    private fun planMediaInfo(minf: Box) {
        val children = boxReader.readChildren(minf)
        children.filter { it.type == HDLR }.forEach { planHandlerName(it) }
        children.filter { it.type == STBL }.forEach { planChildren(boxReader.readChildren(it), keptSampleTableTypes) }
        planChildren(children, keptMediaInfoTypes)
    }

    /*
     * Cameras name themselves here ("GoPro AVC"). Players never read it, and all zeros is an
     * empty name both as ISO's C string and QuickTime's Pascal string. It's not reported: on
     * every other file it would be a muxer's "VideoHandler", which is noise.
     */
    private fun planHandlerName(hdlr: Box) {
        val start = boxReader.fullBoxDataStart(hdlr) + HANDLER_NAME_OFFSET
        if (start < hdlr.end) {
            blanked += BlankedRange(FileSpan(start, hdlr.end))
        }
    }

    // A Pascal string in a fixed 32-byte field. A sample entry too short to have one has nothing to blank.
    private fun planCompressorName(sampleEntry: Box) {
        val start = sampleEntry.dataStart + COMPRESSOR_NAME_OFFSET
        val span = FileSpan(start, start + COMPRESSOR_NAME_SIZE)
        if (span.end > sampleEntry.end) {
            return
        }
        val range = BlankedRange(span)
        blanked += range
        if (range.isAlreadyBlank(reader)) {
            return
        }
        reader.seek(span.start)
        val field = reader.readBytes(COMPRESSOR_NAME_SIZE)
        val length = minOf(field[0].toInt() and 0xFF, COMPRESSOR_NAME_SIZE - 1)
        val name = String(field, 1, length, Charsets.UTF_8).trimEnd('\u0000')
        items += MetadataItem(MetadataCategory.Software, MetadataLabel.COMPRESSOR_NAME, name, COMPRESSOR_NAME_SIZE.toLong())
    }

    private fun addTrackSummaries() {
        if (timestampedTracks > 0) {
            items += MetadataItem(MetadataCategory.DateTime, MetadataLabel.TRACK_TIMESTAMPS, timestampedTracks.toString(), trackTimestampBytes)
        }
        if (unknownHandlers.isNotEmpty()) {
            warnings += MetadataWarning(MetadataWarningKind.UnknownTracksKept, unknownHandlers.toList())
        }
    }

    // endregion Tracks

    // region End of file

    /*
     * Data after the last box is cut off, which is only safe if nothing reads it. Fragments can
     * point there and aren't resolved yet, so they fail the file rather than risk the video.
     */
    private fun planTrailing(trailing: FileSpan?,
                             chunks: List<FileSpan>) {
        if (trailing == null) {
            return
        }
        if (chunks.any { it.end > trailing.start }) {
            throw MalformedFileException("Sample data runs into the trailer at offset ${trailing.start}")
        }
        if (hasFragments) {
            throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Fragmented file with a trailer at offset ${trailing.start}")
        }
        val label = if (endsWithSamsungSignature(trailing)) MetadataLabel.SAMSUNG_TRAILER else MetadataLabel.EMBEDDED_DATA
        items += MetadataItem(MetadataCategory.EmbeddedMedia, label, byteSize = trailing.length)
    }

    private fun endsWithSamsungSignature(trailing: FileSpan): Boolean {
        if (trailing.length < SIGNATURE_SIZE) {
            return false
        }
        reader.seek(trailing.end - SIGNATURE_SIZE)
        return reader.readAscii(SIGNATURE_SIZE) == SAMSUNG_SIGNATURE
    }

    /*
     * The trailer always goes. Removed top-level boxes before it, with any padding among them,
     * are cut too instead of staying as zeroed `free` boxes, unless a sample could reach them.
     */
    private fun outputLength(topLevel: TopLevelBoxes,
                             chunks: List<FileSpan>): Long {
        val boxesEnd = topLevel.trailing?.start ?: reader.size
        if (hasFragments) {
            return boxesEnd
        }
        var cut = boxesEnd
        var cutsRemovedBox = false
        for (box in topLevel.boxes.asReversed()) {
            when {
                box in removedTopLevel -> cutsRemovedBox = true
                box.type in paddingTypes -> Unit
                else -> break
            }
            cut = box.start
        }
        if (cutsRemovedBox == false || chunks.any { it.end > cut }) {
            return boxesEnd
        }
        return cut
    }

    // Overlapping overwrites would depend on order, and overwriting a sample would corrupt the video.
    private fun checkedBlanked(chunks: List<FileSpan>): List<BlankedRange> {
        val sorted = blanked.filter { it.span.length > 0 }.sortedBy { it.span.start }
        for ((previous, next) in sorted.zipWithNext()) {
            if (previous.span.overlaps(next.span)) {
                throw MalformedFileException("Movie metadata at offset ${next.span.start} overlaps other metadata")
            }
        }

        // A sweep rather than a nested loop: a long video has a million chunks.
        val chunksByStart = chunks.sortedBy { it.start }
        var nextChunk = 0
        var reach = 0L
        for (range in sorted) {
            while (nextChunk < chunksByStart.size && chunksByStart[nextChunk].start < range.span.end) {
                reach = maxOf(reach, chunksByStart[nextChunk].end)
                nextChunk++
            }
            if (reach > range.span.start) {
                throw MalformedFileException("Movie metadata at offset ${range.span.start} overlaps sample data")
            }
        }
        return sorted
    }

    // endregion End of file

    // endregion Methods
}
