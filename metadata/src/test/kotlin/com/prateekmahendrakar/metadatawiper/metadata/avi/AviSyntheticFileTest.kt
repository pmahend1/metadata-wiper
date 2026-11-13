package com.prateekmahendrakar.metadatawiper.metadata.avi

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffBuilder.ascii
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffBuilder.chunk
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffBuilder.list
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffBuilder.riff
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffBuilder.text
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffChunk
import com.prateekmahendrakar.metadatawiper.metadata.riff.RiffChunkReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// The camera cases ffmpeg doesn't write: header dates, stream names, vendor lists, stale JUNK and OpenDML continuations.
class AviSyntheticFileTest {

    // region State

    private val harness = HandlerHarness(AviHandler())

    private val mainHeader = chunk("avih", ByteArray(56))
    private val videoHeader = chunk("strh", ascii("vidsMJPG") + ByteArray(48))
    private val videoFormat = chunk("strf", ByteArray(40) { 3 })
    private val codecData = chunk("strd", ascii("CODEC CONFIG"))
    private val movie = list("movi", chunk("00dc", byteArrayOf(1, 2, 3, 4, 5)), chunk("00dc", ByteArray(8) { 9 }))
    private val index = chunk("idx1", ByteArray(32) { 7 })
    private val continuedMovie = list("movi", chunk("00dc", ByteArray(6) { 8 }))
    private val trailer = ascii("TRAILER Jane")

    // Every identifying string the camera file holds outside the media.
    private val identifyingText = listOf("Jane", "NIKON", "CAMERA LEFTOVER", "MORE DATA", "holiday", "SEP 12", "12:30:00", "Joe",
                                         "EXIF", "<x:xmpmeta", "odd", "XYZW")

    private val cameraFile = riff("AVI ",
                                  list("hdrl",
                                       mainHeader,
                                       list("strl",
                                            videoHeader,
                                            videoFormat,
                                            codecData,
                                            chunk("strn", text("Jane camera"))),
                                       list("strl",
                                            chunk("strh", ascii("txts") + ByteArray(52)),
                                            chunk("strf", ByteArray(4)),
                                            chunk("strd", ascii("AVIF") + ascii("EXIF make and model"))),
                                       chunk("IDIT", text("SAT SEP 12 12:30:00 2026\n")),
                                       chunk("ISMP", text("12:30:00:00")),
                                       list("odml", chunk("dmlh", ByteArray(248)), chunk("XYZW", byteArrayOf(1, 2, 3)))),
                                  list("ncdt", chunk("nctg", ascii("NIKON serial 1234"))),
                                  chunk("JUNK", ascii("CAMERA LEFTOVER 5678")),
                                  chunk("JUNK", byteArrayOf(4, 0, 0, 0, 0, 0, 0, 0) + ascii("00dc") + ByteArray(32)),
                                  chunk("_PMX", ascii("<x:xmpmeta>Jane</x:xmpmeta>")),
                                  list("INFO",
                                       chunk("IART", text("Jane Fixture")),
                                       chunk("IKEY", text("holiday")),
                                       chunk("IENG", text("Joe")),
                                       chunk("ICMT", text(""))),
                                  movie,
                                  index,
                                  chunk("ZZZZ", ascii("odd"))) +
                             riff("AVIX", continuedMovie, chunk("JUNK", ascii("MORE DATA"))) +
                             trailer

    private companion object {
        // RIFF header (12) + LIST hdrl header (12) + the `avih` FourCC.
        const val MAIN_HEADER_SIZE_OFFSET = 28
    }

    // endregion State

    // region Methods

    @Test
    fun findsCameraMetadata() {
        val result = harness.inspect(cameraFile)
        val items = result.items
        harness.assertHas(items, MetadataCategory.Other, MetadataLabel.TRACK_NAME, "Jane camera")
        harness.assertHas(items, MetadataCategory.Device, MetadataLabel.VENDOR_DATA, "AVIF")
        harness.assertHas(items, MetadataCategory.DateTime, MetadataLabel.CREATION_TIME, "SAT SEP 12 12:30:00 2026")
        harness.assertHas(items, MetadataCategory.DateTime, MetadataLabel.TIMECODE, "12:30:00:00")
        harness.assertHas(items, MetadataCategory.Other, MetadataLabel.UNKNOWN_CHUNK, "XYZW")
        harness.assertHas(items, MetadataCategory.Device, MetadataLabel.VENDOR_DATA, "ncdt")
        harness.assertHas(items, MetadataCategory.Other, MetadataLabel.XMP)
        harness.assertHas(items, MetadataCategory.People, MetadataLabel.ARTIST, "Jane Fixture")
        harness.assertHas(items, MetadataCategory.Other, "IKEY", "holiday")
        harness.assertHas(items, MetadataCategory.People, "IENG", "Joe")
        harness.assertHas(items, MetadataCategory.Other, MetadataLabel.UNKNOWN_CHUNK, "ZZZZ")
        assertTrue(items.any { it.label == MetadataLabel.EMBEDDED_DATA && it.byteSize == trailer.size.toLong() })

        // The camera's JUNK and the continuation's; the empty index placeholder isn't one.
        assertEquals(2, items.count { it.label == MetadataLabel.LEFTOVER_DATA })
        assertFalse("An empty INFO entry isn't listed", items.any { it.label == MetadataLabel.COMMENT })
        assertEquals(listOf(MetadataWarning(MetadataWarningKind.SubtitleTracksKept)), result.warnings)
    }

    @Test
    fun removesCameraMetadataAndCutsTheTrailer() {
        val (output, report) = harness.strip(cameraFile)

        assertEquals(cameraFile.size - trailer.size, output.size)
        assertEquals(harness.inspect(cameraFile).items, report.removed)
        harness.assertOnlyKeptCategoriesRemain("camera", output)
        assertArrayEquals(output, harness.strip(output).first)
        for (text in identifyingText) {
            assertEquals("Still holds $text", -1, indexOf(output, ascii(text)))
        }
    }

    @Test
    fun keepsMediaStreamHeadersAndLayout() {
        val output = harness.strip(cameraFile).first
        for (kept in listOf(mainHeader, videoHeader, videoFormat, codecData, movie, index, continuedMovie)) {
            val offset = indexOf(cameraFile, kept)
            assertTrue(offset > 0)
            assertArrayEquals(kept, output.copyOfRange(offset, offset + kept.size))
        }

        val inputChunks = chunks(cameraFile)
        val outputChunks = chunks(output)
        assertEquals(inputChunks.map { it.start to it.end }, outputChunks.map { it.start to it.end })
        for ((before, after) in inputChunks.zip(outputChunks).filter { it.first.fourCc != it.second.fourCc }) {
            assertEquals("JUNK", after.fourCc)
            assertEquals(before.dataLength, after.dataLength)
        }
    }

    @Test
    fun keepsAFileWithNothingToRemoveUnchanged() {
        val clean = riff("AVI ", list("hdrl", mainHeader, list("strl", videoHeader, videoFormat)), movie, index)
        val (output, report) = harness.strip(clean)
        assertEquals(emptyList<Any>(), report.removed)
        assertArrayEquals(clean, output)
    }

    @Test
    fun rejectsBrokenStructure() {
        val headers = list("hdrl", mainHeader)
        val broken = mapOf("no header list" to riff("AVI ", movie),
                           "no movie list" to riff("AVI ", headers, index),
                           "no main header" to riff("AVI ", list("hdrl", list("strl", videoHeader)), movie),
                           "not AVI" to riff("WAVE", headers, movie),
                           "list too short" to riff("AVI ", headers, chunk("LIST", ascii("ab")), movie),
                           "chunk past its list" to riff("AVI ", headers, movie).also { it[MAIN_HEADER_SIZE_OFFSET] = 0x7F },
                           "RIFF past the end" to riff("AVI ", headers, movie).also { it[4] = 0x7F },
                           "truncated continuation" to riff("AVI ", headers, movie) + riff("AVIX", continuedMovie).copyOf(20))
        for ((name, bytes) in broken) {
            assertThrows(name, MalformedFileException::class.java) { harness.inspect(bytes) }
        }
    }

    // A RIFF and every chunk inside one, in file order, as far down as the lists the planner reads.
    private fun chunks(bytes: ByteArray): List<RiffChunk> {
        val reader = RiffChunkReader(BinaryReader(ByteArrayChannel(bytes)))
        val all = mutableListOf<RiffChunk>()
        fun walk(chunk: RiffChunk) {
            all += chunk
            if (chunk.fourCc == "LIST" && reader.listType(chunk) in setOf("hdrl", "strl", "odml")) {
                reader.children(chunk).forEach { walk(it) }
            }
        }
        var form = reader.readForm(0, "AVI ")
        while (true) {
            all += form
            reader.children(form).forEach { walk(it) }
            if (reader.hasForm(form.end, "AVIX") == false) {
                return all
            }
            form = reader.readForm(form.end, "AVIX")
        }
    }

    private fun indexOf(bytes: ByteArray,
                        target: ByteArray): Int {
        return (0..bytes.size - target.size).firstOrNull { start -> target.indices.all { bytes[start + it] == target[it] } } ?: -1
    }

    // endregion Methods
}
