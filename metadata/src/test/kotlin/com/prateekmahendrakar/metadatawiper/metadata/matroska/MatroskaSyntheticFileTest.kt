package com.prateekmahendrakar.metadatawiper.metadata.matroska

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlBuilder
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlId
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

class MatroskaSyntheticFileTest {

    // region State

    private val harness = HandlerHarness(MatroskaHandler(FileFormat.WebM))

    private val block = EbmlBuilder.element(EbmlId.SIMPLE_BLOCK, ByteArray(32) { (it + 1).toByte() })

    private val videoTrack = EbmlBuilder.master(EbmlId.TRACK_ENTRY,
                                                EbmlBuilder.unsigned(0xD7L, 1),
                                                EbmlBuilder.unsigned(EbmlId.TRACK_TYPE, 1),
                                                EbmlBuilder.text(0x86L, "V_VP9"))

    private val info = EbmlBuilder.master(EbmlId.INFO,
                                          EbmlBuilder.unsigned(EbmlId.TIMESTAMP_SCALE, 1_000_000),
                                          EbmlBuilder.text(EbmlId.MUXING_APP, "Chrome"),
                                          EbmlBuilder.text(EbmlId.WRITING_APP, "Chrome"))

    // endregion State

    // region Methods

    // As a browser's MediaRecorder writes it: no sizes on Segment or Cluster, no SeekHead or Cues.
    @Test
    fun stripsLiveRecordingWithUnknownSizes() {
        val cluster = EbmlBuilder.unknownSize(EbmlId.CLUSTER, EbmlBuilder.unsigned(0xE7L, 0), block, block)
        val input = file(EbmlBuilder.unknownSize(EbmlId.SEGMENT,
                                                 info,
                                                 EbmlBuilder.master(EbmlId.TRACKS, videoTrack),
                                                 cluster,
                                                 EbmlBuilder.unknownSize(EbmlId.CLUSTER, block)))

        val (output, report) = harness.strip(input)

        harness.assertHas(report.removed, MetadataCategory.Software, "Software", "Chrome")
        assertEquals(input.size, output.size)
        harness.assertOnlyKeptCategoriesRemain("live", output)
        assertEquals(-1, String(output, Charsets.ISO_8859_1).indexOf("Chrome"))
        val clusterStart = input.size - cluster.size - EbmlBuilder.unknownSize(EbmlId.CLUSTER, block).size
        assertArrayEquals(input.copyOfRange(clusterStart, input.size), output.copyOfRange(clusterStart, output.size))
    }

    @Test
    fun voidsUnknownSegmentChildrenAndKeepsUnknownTrackData() {
        val unknownTopLevel = EbmlBuilder.text(0x1A2B3C4DL, "secret")
        val unknownInTrack = EbmlBuilder.text(0x5A5AL, "codec thing")
        val unknownInCluster = EbmlBuilder.text(0x6B6BL, "cluster thing")
        val input = file(EbmlBuilder.master(EbmlId.SEGMENT,
                                            info,
                                            EbmlBuilder.master(EbmlId.TRACKS, EbmlBuilder.master(EbmlId.TRACK_ENTRY, unknownInTrack)),
                                            unknownTopLevel,
                                            EbmlBuilder.master(EbmlId.CLUSTER, block, unknownInCluster)))

        val result = harness.inspect(input)
        harness.assertHas(result.items, MetadataCategory.Other, "Unknown element", "0x1A2B3C4D")
        assertTrue(MetadataWarning(MetadataWarningKind.UnknownElementsKept, listOf("0x5A5A", "0x6B6B")) in result.warnings)

        val output = harness.strip(input).first
        val text = String(output, Charsets.ISO_8859_1)
        assertEquals(-1, text.indexOf("secret"))
        assertTrue(text.contains("codec thing"))
        assertTrue(text.contains("cluster thing"))
    }

    @Test
    fun cutsTrailingDataAfterTheSegment() {
        val segment = EbmlBuilder.master(EbmlId.SEGMENT, info, EbmlBuilder.master(EbmlId.CLUSTER, block))
        val input = file(segment) + "appended by some tool".toByteArray()

        val (output, report) = harness.strip(input)

        assertTrue(report.removed.any { it.label == "Embedded data" && it.byteSize == 21L })
        assertEquals(input.size - 21, output.size)
        assertArrayEquals(output, harness.strip(output).first)
    }

    @Test
    fun refusesASecondSegment() {
        val segment = EbmlBuilder.master(EbmlId.SEGMENT, info)
        val input = file(segment) + file(segment)
        val error = assertThrows(UnsupportedFileException::class.java) { harness.inspect(input) }
        assertEquals(UnsupportedReason.NotYetSupported, error.reason)
    }

    @Test
    fun refusesEncryptedTracks() {
        val encryption = EbmlBuilder.master(EbmlId.CONTENT_ENCODINGS,
                                            EbmlBuilder.master(EbmlId.CONTENT_ENCODING,
                                                               EbmlBuilder.master(EbmlId.CONTENT_ENCRYPTION, EbmlBuilder.unsigned(0x47E1L, 5))))
        val input = file(EbmlBuilder.master(EbmlId.SEGMENT, EbmlBuilder.master(EbmlId.TRACKS, EbmlBuilder.master(EbmlId.TRACK_ENTRY, encryption))))
        val error = assertThrows(UnsupportedFileException::class.java) { harness.inspect(input) }
        assertEquals(UnsupportedReason.Encrypted, error.reason)
    }

    @Test
    fun warnsAboutKeptSubtitlesAndChapterTitles() {
        val subtitles = EbmlBuilder.master(EbmlId.TRACK_ENTRY, EbmlBuilder.unsigned(EbmlId.TRACK_TYPE, 0x11))
        val chapters = EbmlBuilder.master(EbmlId.CHAPTERS,
                                          EbmlBuilder.master(EbmlId.EDITION_ENTRY,
                                                             EbmlBuilder.master(EbmlId.CHAPTER_ATOM,
                                                                                EbmlBuilder.master(EbmlId.CHAPTER_DISPLAY, EbmlBuilder.text(EbmlId.CHAP_STRING, "Arrival")))))
        val input = file(EbmlBuilder.master(EbmlId.SEGMENT, EbmlBuilder.master(EbmlId.TRACKS, videoTrack, subtitles), chapters))

        val warnings = harness.inspect(input).warnings

        assertTrue(MetadataWarning(MetadataWarningKind.SubtitleTracksKept) in warnings)
        assertTrue(MetadataWarning(MetadataWarningKind.ChaptersKept) in warnings)
    }

    @Test
    fun zeroesStaleVoidsAndReportsTheirContents() {
        val staleVoid = EbmlBuilder.text(EbmlId.VOID, "old title")
        val cleanVoid = EbmlBuilder.element(EbmlId.VOID, ByteArray(6))
        val input = EbmlBuilder.header("webm") + cleanVoid + file(EbmlBuilder.master(EbmlId.SEGMENT, info, staleVoid)).drop(EbmlBuilder.header("webm").size).toByteArray()

        val (output, report) = harness.strip(input)

        assertEquals(1, report.removed.count { it.label == "Leftover data" })
        assertEquals(-1, String(output, Charsets.ISO_8859_1).indexOf("old title"))
        assertEquals(input.size, output.size)
    }

    // An inner CRC-32 changes when its TrackEntry does, and the outer one covers that new value.
    @Test
    fun recomputesNestedCrcsInnermostFirst() {
        val namedTrack = EbmlBuilder.masterWithCrc(EbmlId.TRACK_ENTRY,
                                                   EbmlBuilder.unsigned(0xD7L, 1),
                                                   EbmlBuilder.text(EbmlId.NAME, "Jane's camera"))
        val tracks = EbmlBuilder.masterWithCrc(EbmlId.TRACKS, namedTrack)
        val input = file(EbmlBuilder.master(EbmlId.SEGMENT, tracks))

        val output = harness.strip(input).first

        val reader = EbmlReader(BinaryReader(ByteArrayChannel(output)))
        val segment = reader.readElement(reader.readElement(0, output.size.toLong(), 0).end, output.size.toLong(), 0)
        val outerTracks = reader.children(segment).single()
        val entry = reader.children(outerTracks)[1]
        for (owner in listOf(outerTracks, entry)) {
            val crc = reader.children(owner).first()
            val value = CRC32().apply { update(output, crc.end.toInt(), (owner.end - crc.end).toInt()) }.value
            assertArrayEquals(ByteArray(4) { (value shr (8 * it)).toByte() }, reader.readBytes(crc))
        }
        assertEquals(-1, String(output, Charsets.ISO_8859_1).indexOf("Jane"))
    }

    private fun file(segment: ByteArray): ByteArray = EbmlBuilder.header("webm") + segment

    // endregion Methods
}
