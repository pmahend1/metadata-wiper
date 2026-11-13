package com.prateekmahendrakar.metadatawiper.metadata.matroska

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlElement
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlId
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlReader
import com.prateekmahendrakar.metadatawiper.metadata.ebml.VoidWriter
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import java.io.OutputStream
import java.time.Instant
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream

/*
 * Works out what a Matroska or WebM file holds and which bytes a strip overwrites. Nothing
 * moves, because SeekHead and Cues hold Segment offsets: metadata elements become Void elements
 * of the same size, and mandatory strings (MuxingApp, WritingApp) are zeroed in place. The one
 * exception is data after the Segment, which is cut off.
 *
 * Info and Attachments have allowlists, and everything else in them is voided. Tags are voided
 * whole. Inside Tracks and Cluster, unknown elements are kept and reported, because those are
 * where the media lives. A changed element's CRC-32 is recomputed over its new bytes.
 */
class MatroskaStripPlanner(private val reader: BinaryReader) {

    // region State

    private val ebml = EbmlReader(reader)
    private val tagReader = MatroskaTagReader(ebml)

    private val items = mutableListOf<MetadataItem>()
    private val warnings = linkedSetOf<MetadataWarning>()
    private val blanked = mutableListOf<BlankedRange>()
    private val crcByOwner = mutableMapOf<EbmlElement, EbmlElement>()
    private val voidedSegmentChildren = mutableSetOf<Long>()
    private val unknownElements = linkedSetOf<String>()
    private var outputLength = reader.size

    private val keptInfoIds = setOf(EbmlId.TIMESTAMP_SCALE, EbmlId.DURATION, EbmlId.CHAPTER_TRANSLATE, EbmlId.CRC32)

    private val segmentIdIds = setOf(EbmlId.SEGMENT_UUID, EbmlId.PREV_UUID, EbmlId.NEXT_UUID, EbmlId.SEGMENT_FAMILY)

    private val fileNameIds = setOf(EbmlId.SEGMENT_FILENAME, EbmlId.PREV_FILENAME, EbmlId.NEXT_FILENAME)

    // Every TrackEntry child in the Matroska specification apart from Name, CodecName and ContentEncodings, which are handled.
    private val keptTrackEntryIds = setOf(0xD7L, 0x73C5L, EbmlId.TRACK_TYPE, 0xB9L, 0x88L, 0x55AAL, 0x55ABL, 0x55ACL,
                                          0x55ADL, 0x55AEL, 0x55AFL, 0x9CL, 0x6DE7L, 0x6DF8L, 0x23E383L, 0x234E7AL,
                                          0x23314FL, 0x537FL, 0x55EEL, 0x41E4L, 0x22B59CL, 0x22B59DL, 0x86L, 0x63A2L,
                                          0x7446L, 0x3A9697L, 0x3B4040L, 0x26B240L, 0xAAL, 0x6FABL, 0x56AAL, 0x56BBL,
                                          0x6624L, 0xE0L, 0xE1L, 0xE2L, 0xC0L, 0xC1L, 0xC6L, 0xC7L, 0xC4L, EbmlId.CRC32)

    // Timestamp, SilentTracks, Position, PrevSize, and the blocks themselves.
    private val keptClusterIds = setOf(0xE7L, 0x5854L, 0xA7L, 0xABL, EbmlId.SIMPLE_BLOCK, EbmlId.BLOCK_GROUP, EbmlId.CRC32)

    private val fontMediaTypes = setOf("application/x-truetype-font", "application/vnd.ms-opentype", "application/x-font-ttf",
                                       "application/x-font-otf", "application/font-sfnt", "application/font-woff")

    private companion object {
        const val SUBTITLE_TRACK_TYPE = 0x11L
        const val CRC_SIZE = 4L
        const val DATE_SIZE = 8L
        const val NANOS_PER_SECOND = 1_000_000_000L

        // DateUTC counts nanoseconds from the start of 2001.
        const val SECONDS_1970_TO_2001 = 978_307_200L

        const val FONT_MEDIA_TYPE_PREFIX = "font/"
        const val IMAGE_MEDIA_TYPE_PREFIX = "image/"
        const val EBML_MAGIC_SIZE = 4
    }

    // endregion State

    // region Methods

    fun plan(): MatroskaStripPlan {
        val header = ebml.readElement(0, reader.size, 0)
        if (header.id != EbmlId.EBML) {
            throw MalformedFileException("The file doesn't start with an EBML header")
        }
        val segment = findSegment(header.end)
        planSegment(segment)
        planTrailing(segment)
        if (unknownElements.isEmpty() == false) {
            warnings += MetadataWarning(MetadataWarningKind.UnknownElementsKept, unknownElements.toList())
        }

        val changed = blanked.filter { it.isAlreadyBlank(reader) == false }.sortedBy { it.span.start }
        return MatroskaStripPlan(items, warnings.toList(), withRecomputedCrcs(changed), outputLength)
    }

    // Void elements may sit between the EBML header and the Segment.
    private fun findSegment(start: Long): EbmlElement {
        var position = start
        while (position < reader.size) {
            val element = ebml.readElement(position, reader.size, 0)
            when (element.id) {
                EbmlId.SEGMENT -> return element
                EbmlId.VOID -> planVoid(element)
                else -> throw MalformedFileException("Expected a Segment at offset $position")
            }
            position = element.end
        }
        throw MalformedFileException("The file has no Segment")
    }

    private fun planSegment(segment: EbmlElement) {
        val children = ebml.children(segment)
        registerCrc(segment, children)
        for (child in children) {
            when (child.id) {
                EbmlId.SEEK_HEAD, EbmlId.CUES, EbmlId.CRC32 -> Unit
                EbmlId.INFO -> planInfo(child)
                EbmlId.TRACKS -> planTracks(child)
                EbmlId.CLUSTER -> planCluster(child)
                EbmlId.CHAPTERS -> planChapters(child)
                EbmlId.ATTACHMENTS -> planAttachments(child)
                EbmlId.TAGS -> voidSegmentChild(child, tagReader.read(child))
                EbmlId.VOID -> planVoid(child)
                else -> voidSegmentChild(child, listOf(unknown(child)))
            }
        }

        // Last, so it knows every element that was voided.
        for (seekHead in children.filter { it.id == EbmlId.SEEK_HEAD }) {
            planSeekHead(seekHead, segment)
        }
    }

    // region Info

    private fun planInfo(info: EbmlElement) {
        val children = ebml.children(info)
        registerCrc(info, children)
        for (child in children) {
            when (child.id) {
                in keptInfoIds -> Unit
                EbmlId.VOID -> planVoid(child)
                EbmlId.MUXING_APP, EbmlId.WRITING_APP -> zeroText(child, MetadataCategory.Software, MetadataLabel.SOFTWARE)
                EbmlId.TITLE -> remove(child, MetadataCategory.Other, MetadataLabel.TITLE, ebml.readText(child))
                EbmlId.DATE_UTC -> remove(child, MetadataCategory.DateTime, MetadataLabel.CREATION_TIME, dateText(child))
                in segmentIdIds -> remove(child, MetadataCategory.Other, MetadataLabel.SEGMENT_ID)
                in fileNameIds -> remove(child, MetadataCategory.Other, MetadataLabel.FILE_NAME, ebml.readText(child))
                else -> voidElement(child, unknown(child))
            }
        }
    }

    private fun dateText(date: EbmlElement): String {
        if (date.dataLength != DATE_SIZE) {
            return ""
        }
        val nanos = ebml.readUnsigned(date)
        return Instant.ofEpochSecond(SECONDS_1970_TO_2001 + Math.floorDiv(nanos, NANOS_PER_SECOND)).toString()
    }

    // endregion Info

    // region Tracks and clusters

    private fun planTracks(tracks: EbmlElement) {
        val children = ebml.children(tracks)
        registerCrc(tracks, children)
        for (child in children) {
            when (child.id) {
                EbmlId.TRACK_ENTRY -> planTrackEntry(child)
                EbmlId.VOID -> planVoid(child)
                EbmlId.CRC32 -> Unit
                else -> unknownElements += hex(child.id)
            }
        }
    }

    private fun planTrackEntry(entry: EbmlElement) {
        val children = ebml.children(entry)
        registerCrc(entry, children)
        for (child in children) {
            when (child.id) {
                EbmlId.NAME -> remove(child, MetadataCategory.Other, MetadataLabel.TRACK_NAME, ebml.readText(child))
                EbmlId.CODEC_NAME -> remove(child, MetadataCategory.Software, MetadataLabel.COMPRESSOR_NAME, ebml.readText(child))
                EbmlId.CONTENT_ENCODINGS -> rejectEncryption(child)
                EbmlId.VOID -> planVoid(child)
                in keptTrackEntryIds -> Unit
                else -> unknownElements += hex(child.id)
            }
        }
        val type = children.firstOrNull { it.id == EbmlId.TRACK_TYPE }?.let { ebml.readUnsigned(it) }
        if (type == SUBTITLE_TRACK_TYPE) {
            warnings += MetadataWarning(MetadataWarningKind.SubtitleTracksKept)
        }
    }

    // Encrypted blocks can't be checked for metadata, and their keys are elsewhere.
    private fun rejectEncryption(encodings: EbmlElement) {
        val encrypted = ebml.children(encodings).filter { it.id == EbmlId.CONTENT_ENCODING }
                                                .any { encoding -> ebml.children(encoding).any { it.id == EbmlId.CONTENT_ENCRYPTION } }
        if (encrypted) {
            throw UnsupportedFileException(UnsupportedReason.Encrypted, "Track at offset ${encodings.start} is encrypted")
        }
    }

    private fun planCluster(cluster: EbmlElement) {
        val children = ebml.children(cluster)
        registerCrc(cluster, children)
        for (child in children) {
            when (child.id) {
                in keptClusterIds -> Unit
                EbmlId.VOID -> planVoid(child)
                EbmlId.ENCRYPTED_BLOCK -> throw UnsupportedFileException(UnsupportedReason.Encrypted, "Cluster at offset ${cluster.start} has encrypted blocks")
                else -> unknownElements += hex(child.id)
            }
        }
    }

    // endregion Tracks and clusters

    // region Chapters and attachments

    private fun planChapters(chapters: EbmlElement) {
        if (hasChapterTitle(chapters)) {
            warnings += MetadataWarning(MetadataWarningKind.ChaptersKept)
        }
    }

    private fun hasChapterTitle(element: EbmlElement): Boolean {
        return ebml.children(element).any {
            when (it.id) {
                EbmlId.CHAP_STRING -> true
                EbmlId.EDITION_ENTRY, EbmlId.CHAPTER_ATOM, EbmlId.CHAPTER_DISPLAY -> hasChapterTitle(it)
                else -> false
            }
        }
    }

    // Fonts stay so styled subtitles still render. With none to keep, the whole element goes.
    private fun planAttachments(attachments: EbmlElement) {
        val children = ebml.children(attachments)
        val filesChildren = children.filter { it.id == EbmlId.ATTACHED_FILE }.associateWith { ebml.children(it) }
        val fonts = filesChildren.filter { isFont(it.value) }.keys
        if (fonts.isEmpty()) {
            voidSegmentChild(attachments, filesChildren.map { attachmentItem(it.key, it.value) })
            return
        }

        registerCrc(attachments, children)
        for (child in children) {
            when {
                child in fonts -> planFont(child, filesChildren.getValue(child))
                child.id == EbmlId.ATTACHED_FILE -> voidElement(child, attachmentItem(child, filesChildren.getValue(child)))
                child.id == EbmlId.VOID -> planVoid(child)
                child.id == EbmlId.CRC32 -> Unit
                else -> voidElement(child, unknown(child))
            }
        }
        warnings += MetadataWarning(MetadataWarningKind.FontAttachmentsKept)
    }

    private fun planFont(file: EbmlElement,
                         children: List<EbmlElement>) {
        registerCrc(file, children)
        for (child in children) {
            when (child.id) {
                EbmlId.FILE_NAME -> zeroText(child, MetadataCategory.Other, MetadataLabel.FILE_NAME)
                EbmlId.FILE_DESCRIPTION -> zeroText(child, MetadataCategory.Other, MetadataLabel.IMAGE_DESCRIPTION_PROPERTY)
                EbmlId.VOID -> planVoid(child)
            }
        }
    }

    private fun isFont(fileChildren: List<EbmlElement>): Boolean {
        val mediaType = mediaType(fileChildren)
        return mediaType.startsWith(FONT_MEDIA_TYPE_PREFIX) || mediaType in fontMediaTypes
    }

    private fun attachmentItem(file: EbmlElement,
                               children: List<EbmlElement>): MetadataItem {
        val name = children.firstOrNull { it.id == EbmlId.FILE_NAME }?.let { ebml.readText(it) } ?: ""
        if (mediaType(children).startsWith(IMAGE_MEDIA_TYPE_PREFIX)) {
            return MetadataItem(MetadataCategory.Thumbnail, MetadataLabel.THUMBNAIL, name, file.span.length)
        }
        return MetadataItem(MetadataCategory.EmbeddedMedia, MetadataLabel.ATTACHED_FILE, name, file.span.length)
    }

    private fun mediaType(fileChildren: List<EbmlElement>): String {
        return fileChildren.firstOrNull { it.id == EbmlId.FILE_MEDIA_TYPE }?.let { ebml.readText(it).lowercase() } ?: ""
    }

    // endregion Chapters and attachments

    // region Seek heads and the end of the file

    // A Seek entry pointing at a voided element would send a player to a Void, and it names what used to be there.
    private fun planSeekHead(seekHead: EbmlElement,
                             segment: EbmlElement) {
        val children = ebml.children(seekHead)
        registerCrc(seekHead, children)
        for (child in children) {
            when (child.id) {
                EbmlId.SEEK -> {
                    val position = ebml.children(child).firstOrNull { it.id == EbmlId.SEEK_POSITION }?.let { ebml.readUnsigned(it) }
                    if (position != null && segment.dataStart + position in voidedSegmentChildren) {
                        blanked += VoidWriter.blank(child.span)
                    }
                }
                EbmlId.VOID -> planVoid(child)
            }
        }
    }

    // Another EBML header there would be a second Segment, which players can chain; cutting it off would lose video.
    private fun planTrailing(segment: EbmlElement) {
        if (segment.end == reader.size) {
            return
        }
        val trailing = FileSpan(segment.end, reader.size)
        if (trailing.length >= EBML_MAGIC_SIZE) {
            reader.seek(trailing.start)
            if (reader.readU32() == EbmlId.EBML) {
                throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "File has more than one Segment")
            }
        }
        if (BlankedRange(trailing).isAlreadyBlank(reader) == false) {
            items += MetadataItem(MetadataCategory.EmbeddedMedia, MetadataLabel.EMBEDDED_DATA, byteSize = trailing.length)
        }
        outputLength = segment.end
    }

    // endregion Seek heads and the end of the file

    // region Blanking

    private fun voidSegmentChild(child: EbmlElement,
                                 found: List<MetadataItem>) {
        items += found
        blanked += VoidWriter.blank(child.span)
        voidedSegmentChildren += child.start
    }

    private fun remove(element: EbmlElement,
                       category: MetadataCategory,
                       label: String,
                       detail: String = "") {
        voidElement(element, MetadataItem(category, label, detail, element.span.length))
    }

    private fun voidElement(element: EbmlElement,
                            item: MetadataItem) {
        items += item
        blanked += VoidWriter.blank(element.span)
    }

    // For mandatory strings: all zeros is a valid empty string, so the element can stay.
    private fun zeroText(element: EbmlElement,
                         category: MetadataCategory,
                         label: String) {
        val text = ebml.readText(element)
        if (text.isEmpty() == false) {
            items += MetadataItem(category, label, text, element.dataLength)
        }
        blanked += BlankedRange(element.dataSpan)
    }

    // An existing Void can still hold whatever it replaced.
    private fun planVoid(void: EbmlElement) {
        val range = BlankedRange(void.dataSpan)
        if (range.isAlreadyBlank(reader) == false) {
            items += MetadataItem(MetadataCategory.Other, MetadataLabel.LEFTOVER_DATA, byteSize = void.dataLength)
        }
        blanked += range
    }

    private fun unknown(element: EbmlElement): MetadataItem {
        return MetadataItem(MetadataCategory.Other, MetadataLabel.UNKNOWN_ELEMENT, hex(element.id), element.span.length)
    }

    private fun hex(id: Long): String = "0x" + id.toString(16).uppercase()

    // endregion Blanking

    // region CRC-32

    // A CRC-32 element only counts as one when it's the first child, which is the only place the specification allows.
    private fun registerCrc(owner: EbmlElement,
                            children: List<EbmlElement>) {
        val first = children.firstOrNull()
        if (first != null && first.id == EbmlId.CRC32 && first.dataLength == CRC_SIZE) {
            crcByOwner[owner] = first
        }
    }

    /*
     * A CRC-32 covers the rest of its parent's data, so a parent with changed bytes needs a new
     * one. Innermost parents go first: a nested CRC-32 is itself part of what its ancestor covers.
     * Only kept elements register a CRC-32, so none of these sits inside a voided element.
     */
    private fun withRecomputedCrcs(changed: List<BlankedRange>): List<BlankedRange> {
        var ranges = changed
        for ((owner, crc) in crcByOwner.entries.sortedByDescending { it.key.depth }) {
            val covered = FileSpan(crc.end, owner.end)
            val inside = ranges.filter { covered.contains(it.span) }
            if (inside.isEmpty()) {
                continue
            }
            val value = crcOf(covered, inside)
            val bytes = ByteArray(CRC_SIZE.toInt()) { (value shr (8 * it)).toByte() }
            ranges = (ranges + BlankedRange(crc.dataSpan, bytes)).sortedBy { it.span.start }
        }
        return ranges
    }

    // The CRC of what the output will hold there: the input's bytes with [ranges] written over them.
    private fun crcOf(span: FileSpan,
                      ranges: List<BlankedRange>): Long {
        val checksum = CRC32()
        val output = CheckedOutputStream(OutputStream.nullOutputStream(), checksum)
        var position = span.start
        for (range in ranges) {
            reader.copyTo(output, position, range.span.start)
            range.writeTo(output)
            position = range.span.end
        }
        reader.copyTo(output, position, span.end)
        return checksum.value
    }

    // endregion CRC-32

    // endregion Methods
}
