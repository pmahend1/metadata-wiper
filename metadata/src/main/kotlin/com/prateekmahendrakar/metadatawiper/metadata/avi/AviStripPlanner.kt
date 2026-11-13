package com.prateekmahendrakar.metadatawiper.metadata.avi

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffChunk
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffChunkReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Works out what an AVI holds and which bytes a strip overwrites. Nothing moves, because `idx1`
 * offsets are relative to `movi` and OpenDML index offsets are absolute: a metadata chunk is
 * renamed `JUNK` with its size unchanged and its data zeroed, which every player skips. The one
 * exception is data after the last RIFF, which is cut off.
 *
 * Header lists have allowlists, and everything else in them is blanked. `movi` and `idx1` are
 * kept whole, because that's where the media and its index live.
 */
class AviStripPlanner(private val reader: BinaryReader) {

    // region State

    private val riff = RiffChunkReader(reader)

    private val items = mutableListOf<MetadataItem>()
    private val warnings = linkedSetOf<MetadataWarning>()
    private val blanked = mutableListOf<BlankedRange>()
    private var outputLength = reader.size

    private val dates = MetadataCategory.DateTime
    private val people = MetadataCategory.People
    private val software = MetadataCategory.Software
    private val other = MetadataCategory.Other

    private val keptStreamChunks = setOf("strh", "strf", "indx", "vprp")

    // Chunks of the RIFF INFO list. Unlisted ones keep their FourCC as the label.
    private val infoTags = mapOf("IART" to (people to MetadataLabel.ARTIST),
                                 "ICOP" to (people to MetadataLabel.COPYRIGHT),
                                 "ICRD" to (dates to MetadataLabel.CREATION_TIME),
                                 "ISFT" to (software to MetadataLabel.SOFTWARE),
                                 "INAM" to (other to MetadataLabel.TITLE),
                                 "ICMT" to (other to MetadataLabel.COMMENT),
                                 "ISBJ" to (other to MetadataLabel.IMAGE_DESCRIPTION_PROPERTY))

    // Engineer, technician, commissioned by, starring, director, producer, production designer, editor, distributed by.
    private val peopleInfoTags = setOf("IENG", "ITCH", "ICMS", "ISTR", "IDIR", "IPRO", "IPDS", "IEDT", "IDST")

    // Camera vendor lists: Nikon, Pentax (two), Canon, and the `exif` list some cameras write with make, model and date.
    private val vendorLists = setOf("ncdt", "hydt", "pntx", "CNTH", "exif")

    // `strd` is codec data, but some cameras put an EXIF block (`AVIF`), Casio data or a vendor name there instead.
    private val vendorStreamData = setOf("AVIF", "CASI", "Zora")

    private companion object {
        const val AVI = "AVI "
        const val AVIX = "AVIX"
        const val HDRL = "hdrl"
        const val STRL = "strl"
        const val ODML = "odml"
        const val MOVI = "movi"
        const val INFO = "INFO"
        const val JUNK = "JUNK"
        const val AVIH = "avih"
        const val STRH = "strh"
        const val STRD = "strd"
        const val STRN = "strn"
        const val DMLH = "dmlh"
        const val IDX1 = "idx1"
        const val IDIT = "IDIT"
        const val ISMP = "ISMP"
        const val XMP = "_PMX"
        const val SUBTITLE_STREAM_TYPE = "txts"

        // Display only: a longer text chunk is still blanked whole.
        const val MAX_TEXT_SIZE = 1024

        // Muxers reserve JUNK for an OpenDML index or header written later; empty, it holds these 12 bytes and zeros.
        const val PLACEHOLDER_HEADER_SIZE = 12
        val EMPTY_INDEX_PREFIX = byteArrayOf(4, 0, 0, 0, 0, 0, 0, 0)
        val EMPTY_ODML_PREFIX = "odmldmlh".toByteArray(Charsets.US_ASCII)
    }

    // endregion State

    // region Methods

    fun plan(): AviStripPlan {
        val forms = mutableListOf(riff.readForm(0, AVI))
        while (riff.hasForm(forms.last().end, AVIX)) {
            forms += riff.readForm(forms.last().end, AVIX)
        }

        val firstChunks = riff.children(forms.first())
        val headers = firstChunks.firstOrNull { riff.isList(it, HDRL) } ?: throw MalformedFileException("AVI has no header list")
        if (firstChunks.none { riff.isList(it, MOVI) }) {
            throw MalformedFileException("AVI has no movie data list")
        }
        planHeaders(headers)
        for (form in forms) {
            planForm(form, headers)
        }
        planTrailing(forms.last().end)

        val changed = blanked.filter { it.isAlreadyBlank(reader) == false }.sortedBy { it.span.start }
        return AviStripPlan(items, warnings.toList(), changed, outputLength)
    }

    private fun planForm(form: RiffChunk,
                         headers: RiffChunk) {
        for (chunk in riff.children(form)) {
            when {
                chunk == headers || riff.isList(chunk, MOVI) || chunk.fourCc == IDX1 -> Unit
                else -> planOther(chunk)
            }
        }
    }

    // region Header lists

    private fun planHeaders(headers: RiffChunk) {
        val children = riff.children(headers)
        if (children.firstOrNull()?.fourCc != AVIH) {
            throw MalformedFileException("AVI header list doesn't start with a main header")
        }
        for (child in children) {
            when {
                child.fourCc == AVIH -> Unit
                riff.isList(child, STRL) -> planStream(child)
                riff.isList(child, ODML) -> planOpenDml(child)
                else -> planOther(child)
            }
        }
    }

    private fun planStream(stream: RiffChunk) {
        for (child in riff.children(stream)) {
            when (child.fourCc) {
                STRH -> warnIfSubtitles(child)
                in keptStreamChunks -> Unit
                STRD -> planStreamData(child)
                STRN -> blank(child, other, MetadataLabel.TRACK_NAME, text(child))
                else -> planOther(child)
            }
        }
    }

    private fun warnIfSubtitles(header: RiffChunk) {
        if (header.dataLength >= RiffChunkReader.FOURCC_SIZE && fourCcAt(header.dataStart) == SUBTITLE_STREAM_TYPE) {
            warnings += MetadataWarning(MetadataWarningKind.SubtitleTracksKept)
        }
    }

    private fun planStreamData(data: RiffChunk) {
        if (data.dataLength < RiffChunkReader.FOURCC_SIZE) {
            return
        }
        val signature = fourCcAt(data.dataStart)
        if (signature in vendorStreamData) {
            blank(data, MetadataCategory.Device, MetadataLabel.VENDOR_DATA, signature.trim())
        }
    }

    private fun planOpenDml(openDml: RiffChunk) {
        for (child in riff.children(openDml)) {
            when (child.fourCc) {
                DMLH -> Unit
                else -> planOther(child)
            }
        }
    }

    // endregion Header lists

    // region Other chunks

    // Anything outside the allowlists, at the top level or in a header list.
    private fun planOther(chunk: RiffChunk) {
        when (chunk.fourCc) {
            JUNK -> planJunk(chunk)
            IDIT -> blank(chunk, dates, MetadataLabel.CREATION_TIME, text(chunk))
            ISMP -> blank(chunk, dates, MetadataLabel.TIMECODE, text(chunk))
            XMP -> blank(chunk, other, MetadataLabel.XMP)
            RiffChunkReader.LIST -> planList(chunk)
            else -> blank(chunk, other, MetadataLabel.UNKNOWN_CHUNK, chunk.fourCc.trim())
        }
    }

    private fun planList(list: RiffChunk) {
        val listType = riff.listType(list)
        when (listType) {
            INFO -> blank(list, infoItems(list))
            in vendorLists -> blank(list, MetadataCategory.Device, MetadataLabel.VENDOR_DATA, listType.trim())
            else -> blank(list, other, MetadataLabel.UNKNOWN_CHUNK, listType.trim())
        }
    }

    // Display only: the list is blanked whatever it holds, so one that can't be parsed is reported whole.
    private fun infoItems(info: RiffChunk): List<MetadataItem> {
        val children = try {
            riff.children(info)
        } catch (_: MalformedFileException) {
            return listOf(MetadataItem(other, MetadataLabel.TAGS, byteSize = info.dataLength))
        }
        return children.mapNotNull { child ->
            val value = text(child)
            if (value.isEmpty()) {
                return@mapNotNull null
            }
            val (category, label) = infoTag(child.fourCc)
            MetadataItem(category, label, value, child.dataLength)
        }
    }

    private fun infoTag(fourCc: String): Pair<MetadataCategory, String> {
        val known = infoTags[fourCc]
        if (known != null) {
            return known
        }
        return (if (fourCc in peopleInfoTags) people else other) to fourCc.trim()
    }

    // Some cameras put data in JUNK, so it's zeroed unless it's only an empty placeholder.
    private fun planJunk(junk: RiffChunk) {
        if (isEmptyPlaceholder(junk)) {
            return
        }
        val range = BlankedRange(FileSpan(junk.dataStart, junk.end))
        if (range.isAlreadyBlank(reader) == false) {
            items += MetadataItem(other, MetadataLabel.LEFTOVER_DATA, byteSize = junk.dataLength)
        }
        blanked += range
    }

    private fun isEmptyPlaceholder(junk: RiffChunk): Boolean {
        if (junk.dataLength < PLACEHOLDER_HEADER_SIZE) {
            return false
        }
        reader.seek(junk.dataStart)
        val header = reader.readBytes(PLACEHOLDER_HEADER_SIZE)
        val prefix = header.copyOf(EMPTY_INDEX_PREFIX.size)
        if (prefix.contentEquals(EMPTY_INDEX_PREFIX) == false && prefix.contentEquals(EMPTY_ODML_PREFIX) == false) {
            return false
        }
        return BlankedRange(FileSpan(junk.dataStart + PLACEHOLDER_HEADER_SIZE, junk.end)).isAlreadyBlank(reader)
    }

    // A second RIFF of another type is still data after the movie, so it's cut off with the rest.
    private fun planTrailing(lastFormEnd: Long) {
        if (lastFormEnd == reader.size) {
            return
        }
        val trailing = FileSpan(lastFormEnd, reader.size)
        if (BlankedRange(trailing).isAlreadyBlank(reader) == false) {
            items += MetadataItem(MetadataCategory.EmbeddedMedia, MetadataLabel.EMBEDDED_DATA, byteSize = trailing.length)
        }
        outputLength = lastFormEnd
    }

    // endregion Other chunks

    // region Blanking

    private fun blank(chunk: RiffChunk,
                      category: MetadataCategory,
                      label: String,
                      detail: String = "") {
        blank(chunk, listOf(MetadataItem(category, label, detail, chunk.dataLength)))
    }

    // The chunk keeps its size and its pad byte, so everything after it stays where it was.
    private fun blank(chunk: RiffChunk,
                      found: List<MetadataItem>) {
        items += found
        val header = ByteBuffer.allocate(RiffChunkReader.CHUNK_HEADER_SIZE.toInt())
                               .order(ByteOrder.LITTLE_ENDIAN)
                               .put(JUNK.toByteArray(Charsets.US_ASCII))
                               .putInt(chunk.dataLength.toInt())
                               .array()
        blanked += BlankedRange(FileSpan(chunk.start, chunk.end), header)
    }

    private fun text(chunk: RiffChunk): String {
        reader.seek(chunk.dataStart)
        val bytes = reader.readBytes(minOf(chunk.dataLength, MAX_TEXT_SIZE.toLong()).toInt())
        val length = bytes.indexOf(0).let { if (it == -1) bytes.size else it }
        return String(bytes, 0, length, Charsets.UTF_8).trim()
    }

    private fun fourCcAt(position: Long): String {
        reader.seek(position)
        return reader.readAscii(RiffChunkReader.FOURCC_SIZE)
    }

    // endregion Blanking

    // endregion Methods
}
