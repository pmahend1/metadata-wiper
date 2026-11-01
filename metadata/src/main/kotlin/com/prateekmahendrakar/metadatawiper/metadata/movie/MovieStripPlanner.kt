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
 * Each level of the box tree has an allowlist, and anything else is freed. Timed-metadata tracks
 * are freed and timecode tracks kept, and both have their samples zeroed in `mdat`, where they'd
 * otherwise stay readable. So do bytes in `mdat` that no track references, as long as every
 * structure that can point there is understood (see planGaps).
 */
class MovieStripPlanner(private val reader: BinaryReader) {

    // region State

    private val boxReader = BoxReader(reader)
    private val trackReader = MovieTrackReader(reader, boxReader)
    private val metadataReader = MovieMetadataReader(reader, boxReader)
    private val fragmentReader = MovieFragmentReader(reader, boxReader)

    private val items = mutableListOf<MetadataItem>()
    private val warnings = linkedSetOf<MetadataWarning>()
    private val blanked = mutableListOf<BlankedRange>()
    private val removedTopLevel = mutableSetOf<Box>()
    private val unknownHandlers = linkedSetOf<String>()
    private val keptChunks = mutableListOf<FileSpan>()
    private val zeroedChunks = mutableListOf<FileSpan>()
    private var hasAuxiliaryOffsets = false
    private var hasShortChunks = false
    private var timestampedTracks = 0
    private var trackTimestampBytes = 0L
    private var producerTimeBytes = 0L

    private val paddingTypes = setOf(FREE, SKIP, WIDE)

    private val keptTopLevelTypes = setOf(FTYP, MDAT, MOOV, MOOF, "mfra", "sidx", "ssix", "styp", "emsg", "pdin")

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
        const val TREF = "tref"
        const val MVEX = "mvex"
        const val PRFT = "prft"
        const val SAIO = "saio"
        const val RTMD = "rtmd"
        const val TMCD = "tmcd"
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
        const val TRACK_ID_SIZE = 4L
        const val NTP_TIME_SIZE = 8L

        // After `hdlr`'s version and flags: component type, handler type, and 12 reserved bytes.
        const val HANDLER_NAME_OFFSET = 20L

        // A timecode sample entry's fixed fields; a `name` box with the reel name can follow them.
        const val TIMECODE_ENTRY_SIZE = 26L

        // QuickTime PCM's "one frame" sample size, which doesn't describe the bytes a chunk holds.
        const val FRAME_SIZED_SAMPLE = 1L

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
        val mediaData = topLevel.boxes.filter { it.type == MDAT }.map { FileSpan(it.dataStart, it.end) }

        planInStreamWarning(topLevel.boxes)
        planMovie(movie, topLevel.boxes.filter { it.type == MOOF }, mediaData)
        planTopLevel(topLevel.boxes)
        planTrailing(topLevel.trailing, keptChunks + zeroedChunks)
        planGaps(mediaData, topLevel.boxes)
        addSummaries()

        val outputLength = outputLength(topLevel)
        val blankedRanges = checkedBlanked()
        return MovieStripPlan(items.toList(), warnings.toList(), blankedRanges.filter { it.span.start < outputLength }, outputLength, keptChunks.toList())
    }

    // Plans just a `moov`, for a container that has its own idea of what else is in the file (a HEIF image sequence).
    fun planMovieBox(moov: Box,
                     mediaData: List<FileSpan>): MovieStripPlan {
        planMovie(moov, emptyList(), mediaData)
        addSummaries()
        return MovieStripPlan(items.toList(), warnings.toList(), checkedBlanked(), reader.size, keptChunks.toList())
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

    private fun planTopLevel(topLevel: List<Box>) {
        for (box in topLevel) {
            when (box.type) {
                PRFT -> planProducerTime(box)
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

    private fun planMovie(moov: Box,
                          moofs: List<Box>,
                          mediaData: List<FileSpan>) {
        val movieChildren = boxReader.readChildren(moov)
        rejectUnreadableMovie(movieChildren)
        val tracks = movieChildren.filter { it.type == TRAK }.map { trackReader.read(it) }
        val trackExtends = movieChildren.filter { it.type == MVEX }.flatMap { fragmentReader.readTrackExtends(it) }
        val defaultSizes = trackExtends.associate { it.trackId to it.defaultSampleSize }
        val fragments = moofs.map { it to fragmentReader.read(it, defaultSizes) }
        val runs = fragments.flatMap { it.second }.groupBy({ it.trackId }, { it.runs })

        val unknownTrack = runs.keys.firstOrNull { id -> tracks.none { it.id == id } }
        if (unknownTrack != null) {
            throw MalformedFileException("Fragment of track $unknownTrack, which the movie doesn't have")
        }

        movieChildren.filter { it.type == MVHD }.forEach { planMovieHeader(it) }

        val chapterTargets = tracks.flatMap { it.chapterTrackIds }.toSet()
        val removedIds = tracks.filter { it.kind(chapterTargets) == TrackKind.TimedMetadata }.map { it.id }.toSet()
        for (track in tracks) {
            val chunks = (track.sampleTable.chunks + runs[track.id].orEmpty().flatten()).filter { it.length > 0 }
            planTrack(track, chunks, chapterTargets, removedIds)
        }
        trackExtends.filter { it.trackId in removedIds }.forEach { blanked += FreedBox.ranges(it.box) }
        fragments.forEach { (moof, trackFragments) -> planFragment(moof, trackFragments, removedIds) }
        planChildren(movieChildren, keptMovieTypes)
        planZeroedChunks(mediaData)
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

    private fun planFragment(moof: Box,
                             fragments: List<TrackFragment>,
                             removedIds: Set<Long>) {
        for ((previous, next) in fragments.zipWithNext()) {
            if (previous.trackId in removedIds && next.trackId !in removedIds && next.followsPreviousFragment) {
                throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Track ${next.trackId}'s fragment depends on a removed one")
            }
        }
        for (fragment in fragments) {
            if (fragment.trackId in removedIds) {
                blanked += FreedBox.ranges(fragment.box)
                continue
            }
            val children = boxReader.readChildren(fragment.box)
            hasAuxiliaryOffsets = hasAuxiliaryOffsets || children.any { it.type == SAIO }
            planChildren(children, keptFragmentTrackTypes)
        }
        planChildren(boxReader.readChildren(moof), keptFragmentTypes)
    }

    // The NTP wall-clock time of a capture. Only low-latency players read it, and for latency, so zero costs nothing.
    private fun planProducerTime(prft: Box) {
        val start = boxReader.fullBoxDataStart(prft) + TRACK_ID_SIZE
        if (prft.end - start < NTP_TIME_SIZE) {
            throw MalformedFileException("'prft' box at offset ${prft.start} is too short")
        }
        val range = BlankedRange(FileSpan(start, start + NTP_TIME_SIZE))
        blanked += range
        if (range.isAlreadyBlank(reader) == false) {
            producerTimeBytes += range.span.length
        }
    }

    // endregion Movie

    // region Tracks

    private fun planTrack(track: MovieTrack,
                          chunks: List<FileSpan>,
                          chapterTargets: Set<Long>,
                          removedIds: Set<Long>) {
        val kind = track.kind(chapterTargets)
        if (kind == TrackKind.TimedMetadata) {
            planTelemetry(track, chunks)
            return
        }

        if (kind == TrackKind.Timecode) {
            planTimecode(track, chunks)
        } else {
            keptChunks += chunks
            hasShortChunks = hasShortChunks || track.sampleTable.constantSampleSize == FRAME_SIZED_SAMPLE
        }
        when (kind) {
            TrackKind.Subtitle -> warnings += MetadataWarning(MetadataWarningKind.SubtitleTracksKept)
            TrackKind.Chapter -> warnings += MetadataWarning(MetadataWarningKind.ChaptersKept)
            TrackKind.Unknown -> unknownHandlers += track.handlerType
            TrackKind.Video -> track.sampleEntries.forEach { planCompressorName(it) }
            else -> Unit
        }

        val children = boxReader.readChildren(track.box)
        val headers = children.filter { it.type == TKHD } +
                      children.filter { it.type == MDIA }.flatMap { boxReader.readChildren(it) }.filter { it.type == MDHD }
        val spans = headers.flatMap { timeSpans(it).toList() }
        planTrackTimes(spans)
        children.filter { it.type == MDIA }.forEach { planMedia(it) }
        children.filter { it.type == TREF }.forEach { planReferences(it, removedIds) }
        planChildren(children, keptTrackTypes)
    }

    // The whole `trak` goes, but its samples would stay readable in `mdat` to anything that scans for them.
    private fun planTelemetry(track: MovieTrack,
                              chunks: List<FileSpan>) {
        val formats = track.sampleEntries.map { it.type }
        if (RTMD in formats) {
            warnings += MetadataWarning(MetadataWarningKind.InStreamMetadataNotChecked)
        }
        val format = formats.firstOrNull() ?: track.handlerType
        items += MetadataItem(MetadataCategory.Location, MetadataLabel.TELEMETRY_TRACK, format, chunks.sumOf { it.length })
        blanked += FreedBox.ranges(track.box)
        zeroedChunks += chunks
    }

    // The start timecode is often the time of day. All zeros is a valid 00:00:00:00, and reel names are never read back.
    private fun planTimecode(track: MovieTrack,
                             chunks: List<FileSpan>) {
        val names = track.sampleEntries.filter { it.type == TMCD && it.end - it.dataStart > TIMECODE_ENTRY_SIZE }
                                       .map { BlankedRange(FileSpan(it.dataStart + TIMECODE_ENTRY_SIZE, it.end)) }
        blanked += names
        zeroedChunks += chunks

        val found = (FileSpan.merged(chunks).map { BlankedRange(it) } + names).filter { it.isAlreadyBlank(reader) == false }
        if (found.isNotEmpty()) {
            items += MetadataItem(MetadataCategory.DateTime, MetadataLabel.TIMECODE, byteSize = found.sumOf { it.span.length })
        }
    }

    // A reference to a freed track would dangle, and readers can refuse a file over that.
    private fun planReferences(tref: Box,
                               removedIds: Set<Long>) {
        for (reference in boxReader.readChildren(tref)) {
            reader.seek(reference.dataStart)
            val idCount = (reference.end - reference.dataStart) / TRACK_ID_SIZE
            if ((0 until idCount).any { reader.readU32() in removedIds }) {
                blanked += FreedBox.ranges(reference)
            }
        }
    }

    // Merged, because tracks that share a chunk are fine as long as neither is kept. Blanking outside `mdat` could hit structure.
    private fun planZeroedChunks(mediaData: List<FileSpan>) {
        for (span in FileSpan.merged(zeroedChunks)) {
            val index = mediaData.binarySearchBy(span.start) { it.start }
            val container = mediaData.getOrNull(if (index >= 0) index else -index - 2)
            if (container == null || container.contains(span) == false) {
                throw MalformedFileException("Removed samples at offset ${span.start} aren't in the media data")
            }
            blanked += BlankedRange(span)
        }
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
        children.filter { it.type == STBL }.forEach { planSampleTable(it) }
        planChildren(children, keptMediaInfoTypes)
    }

    private fun planSampleTable(stbl: Box) {
        val children = boxReader.readChildren(stbl)
        hasAuxiliaryOffsets = hasAuxiliaryOffsets || children.any { it.type == SAIO }
        planChildren(children, keptSampleTableTypes)
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

    private fun addSummaries() {
        if (timestampedTracks > 0) {
            items += MetadataItem(MetadataCategory.DateTime, MetadataLabel.TRACK_TIMESTAMPS, timestampedTracks.toString(), trackTimestampBytes)
        }
        if (producerTimeBytes > 0) {
            items += MetadataItem(MetadataCategory.DateTime, MetadataLabel.TIMESTAMP, byteSize = producerTimeBytes)
        }
        if (unknownHandlers.isNotEmpty()) {
            warnings += MetadataWarning(MetadataWarningKind.UnknownTracksKept, unknownHandlers.toList())
        }
    }

    // endregion Tracks

    // region Media data

    /*
     * Whatever no track references is a leftover: removed telemetry, a dashcam's GPS blocks, data
     * an editor orphaned. "Referenced" is only as good as our list of what can point into `mdat`,
     * so with `saio` offsets, an `iloc`, or chunk spans known to be too short, the gaps may not
     * be gaps. Those files keep them, and a warning says so, because zeroing by guess could
     * destroy samples.
     */
    private fun planGaps(mediaData: List<FileSpan>,
                         topLevel: List<Box>) {
        val found = gaps(mediaData, FileSpan.merged(keptChunks + zeroedChunks)).map { BlankedRange(it) }.filter { it.isAlreadyBlank(reader) == false }
        if (found.isEmpty()) {
            return
        }
        val size = found.sumOf { it.span.length }
        val hasItemLocations = topLevel.any { it.type == META && metadataReader.hasItemLocations(it) }
        if (hasAuxiliaryOffsets || hasShortChunks || hasItemLocations) {
            warnings += MetadataWarning(MetadataWarningKind.UnreferencedDataKept, listOf(size.toString()))
            return
        }
        blanked += found
        items += MetadataItem(MetadataCategory.Other, MetadataLabel.UNREFERENCED_DATA, byteSize = size)
    }

    // Both lists are sorted, so one sweep finds what's in [mediaData] but not in [referenced].
    private fun gaps(mediaData: List<FileSpan>,
                     referenced: List<FileSpan>): List<FileSpan> {
        val gaps = mutableListOf<FileSpan>()
        var next = 0
        for (container in mediaData) {
            var position = container.start
            while (next < referenced.size && referenced[next].start < container.end) {
                val span = referenced[next]
                if (span.start > position) {
                    gaps += FileSpan(position, span.start)
                }
                position = maxOf(position, span.end)
                if (span.end > container.end) {
                    break
                }
                next++
            }
            if (position < container.end) {
                gaps += FileSpan(position, container.end)
            }
        }
        return gaps
    }

    // endregion Media data

    // region End of file

    // Data after the last box is cut off, which is only safe if nothing reads it.
    private fun planTrailing(trailing: FileSpan?,
                             chunks: List<FileSpan>) {
        if (trailing == null) {
            return
        }
        if (chunks.any { it.end > trailing.start }) {
            throw MalformedFileException("Sample data runs into the trailer at offset ${trailing.start}")
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
    private fun outputLength(topLevel: TopLevelBoxes): Long {
        val boxesEnd = topLevel.trailing?.start ?: reader.size
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
        if (cutsRemovedBox == false || keptChunks.any { it.end > cut }) {
            return boxesEnd
        }
        return cut
    }

    // Overlapping overwrites would depend on order, and overwriting a sample would corrupt the video.
    private fun checkedBlanked(): List<BlankedRange> {
        val sorted = blanked.filter { it.span.length > 0 }.sortedBy { it.span.start }
        for ((previous, next) in sorted.zipWithNext()) {
            if (previous.span.overlaps(next.span)) {
                throw MalformedFileException("Movie metadata at offset ${next.span.start} overlaps other metadata")
            }
        }

        val overlapped = FileSpan.firstOverlap(sorted.map { it.span }, keptChunks)
        if (overlapped != null) {
            throw MalformedFileException("Movie metadata at offset ${overlapped.start} overlaps sample data")
        }
        return sorted
    }

    // endregion End of file

    // endregion Methods
}
