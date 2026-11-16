package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.SampleTableReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// What a strip does to `mdat`: telemetry and timecode samples, and bytes nothing references.
class MovieSamplesTest {

    // region State

    private val harness = HandlerHarness(IsoMediaHandler(FileFormat.Mp4))

    private val secret = "48.8582N".toByteArray()

    private val videoSamples = listOf(listOf(ByteArray(64) { 7 }), listOf(ByteArray(64) { 9 }))

    private val telemetrySamples = listOf(listOf(secret), listOf(secret + secret))

    private fun video(trakExtras: List<ByteArray> = emptyList(),
                      stblExtras: List<ByteArray> = emptyList()): MovieTestTrack {
        return MovieTestTrack("vide", "avc1", videoSamples, trakExtras = trakExtras, stblExtras = stblExtras)
    }

    private fun telemetry(format: String = "gpmd",
                          offsetShift: Long = 0): MovieTestTrack {
        return MovieTestTrack("meta", format, telemetrySamples, offsetShift = offsetShift)
    }

    // endregion State

    // region Methods

    // region Telemetry

    @Test
    fun removesTelemetryTracksAndZeroesTheirSamples() {
        val built = MovieBuilder(listOf(video(), telemetry())).build()

        val (output, report) = harness.strip(built.bytes)
        assertTrue(report.removed.contains(MetadataItem(MetadataCategory.Location, "Telemetry track", "gpmd", 24)))
        assertEquals(built.bytes.size, output.size)
        assertEquals(-1, output.indexOf(secret))
        assertEquals(1, tracks(output).size)

        assertKept(built.bytes, output, built.chunkSpans[0])
        assertZeroed(output, built.chunkSpans[1])
        assertClean(output)
    }

    @Test
    fun removesTelemetryWithLargeOffsetsAndCompactSizes() {
        val camera = MovieTestTrack("camm", "camm", telemetrySamples, sizeBits = 16, largeOffsets = true)
        val built = MovieBuilder(listOf(video(), camera)).build()

        val output = harness.strip(built.bytes).first
        assertZeroed(output, built.chunkSpans[1])
        assertClean(output)
    }

    @Test
    fun recognisesEveryTelemetryFormat() {
        val formats = listOf("meta" to "gpmd", "camm" to "camm", "meta" to "mebx", "vide" to "rtmd", "vide" to "djmd", "text" to "fdsc")
        for ((handler, format) in formats) {
            val built = MovieBuilder(listOf(video(), MovieTestTrack(handler, format, telemetrySamples))).build()
            assertEquals(format, -1, harness.strip(built.bytes).first.indexOf(secret))
        }
    }

    @Test
    fun aTelemetryTrackThatSharesAChunkWithAnotherIsStillRemoved() {
        val shared = telemetry(offsetShift = -8)
        val built = MovieBuilder(listOf(telemetry(), shared)).build()
        assertEquals(-1, harness.strip(built.bytes).first.indexOf(secret))
    }

    @Test
    fun rejectsTelemetryOverlappingAKeptTrack() {
        val built = MovieBuilder(listOf(video(), telemetry(offsetShift = -16))).build()
        assertRejected(built.bytes)
    }

    @Test
    fun rejectsTelemetryOutsideTheMediaData() {
        val built = MovieBuilder(listOf(telemetry(offsetShift = -100))).build()
        assertRejected(built.bytes)
    }

    @Test
    fun freesReferencesToRemovedTracksOnly() {
        val references = box("tref", box("cdsc", u32(2)), box("tmcd", u32(3)))
        val timecode = MovieTestTrack("tmcd", "tmcd", listOf(listOf(ByteArray(4))))
        val built = MovieBuilder(listOf(video(trakExtras = listOf(references)), telemetry(), timecode)).build()

        val output = harness.strip(built.bytes).first
        val tref = findIn(output, "tref").single()
        val kept = BoxReader(BinaryReader(ByteArrayChannel(output))).readChildren(tref)
        assertEquals(listOf("free", "tmcd"), kept.map { it.type })
        assertClean(output)
    }

    @Test
    fun removesTelemetryFromRealMedia() {
        for (name in listOf("mp4-android.mp4", "mp4-fragmented.mp4")) {
            val input = audioAsTelemetry(harness.fixture(name))
            val chunks = sampleTableChunks(harness.fixture(name))
            val (output, report) = harness.strip(input)

            assertTrue(name, report.removed.any { it.label == "Telemetry track" && it.detail == "gpmd" })
            assertEquals(name, 1, tracks(output).size)
            assertKeptApartFromUserData(input, output, chunks[0])
            assertZeroed(output, chunks[1])
            assertClean(output)
        }
    }

    // endregion Telemetry

    // region Timecode

    @Test
    fun zeroesTimecodeSamplesAndKeepsTheTrack() {
        val frameNumber = byteArrayOf(0x01, 0x23, 0x45, 0x67)
        val reelName = box("name", "Jane's reel".toByteArray())
        val timecode = MovieTestTrack("tmcd", "tmcd", listOf(listOf(frameNumber)), sampleEntryExtras = ByteArray(18) + reelName)
        val built = MovieBuilder(listOf(video(), timecode)).build()

        val (output, report) = harness.strip(built.bytes)
        assertEquals(4L + reelName.size, report.removed.single { it.label == "Timecode" }.byteSize)
        assertEquals(2, tracks(output).size)
        assertZeroed(output, built.chunkSpans[1])
        assertKept(built.bytes, output, built.chunkSpans[0])
        assertEquals(-1, output.indexOf(frameNumber))
        assertEquals(-1, output.indexOf("Jane".toByteArray()))
        assertClean(output)
    }

    @Test
    fun aZeroTimecodeIsNotReported() {
        val timecode = MovieTestTrack("tmcd", "tmcd", listOf(listOf(ByteArray(4))))
        val input = MovieBuilder(listOf(video(), timecode)).build().bytes
        assertEquals(emptyList<MetadataItem>(), harness.inspect(input).items)
    }

    // endregion Timecode

    // region Gaps

    @Test
    fun zeroesBytesNoTrackReferences() {
        val movie = MovieBuilder(listOf(video()))
        movie.unreferencedMedia = secret
        val built = movie.build()

        val (output, report) = harness.strip(built.bytes)
        assertTrue(report.removed.contains(MetadataItem(MetadataCategory.Other, "Unreferenced data", "", secret.size.toLong())))
        assertEquals(built.bytes.size, output.size)
        assertEquals(-1, output.indexOf(secret))
        assertKept(built.bytes, output, built.chunkSpans[0])
        assertClean(output)
    }

    @Test
    fun ignoresGapsThatAreAlreadyZero() {
        val movie = MovieBuilder(listOf(video()))
        movie.unreferencedMedia = ByteArray(32)
        val input = movie.build().bytes
        assertEquals(emptyList<MetadataItem>(), harness.inspect(input).items)
        assertArrayEquals(input, harness.strip(input).first)
    }

    @Test
    fun keepsGapsWhenAuxiliaryOffsetsMightPointAtThem() {
        val auxiliary = fullBox("saio", 0, 0, u32(0))
        assertGapKept(MovieBuilder(listOf(video(stblExtras = listOf(auxiliary)))))
    }

    @Test
    fun keepsGapsWhenAnItemLocationMightPointAtThem() {
        val movie = MovieBuilder(listOf(video()))
        movie.topLevelExtras = listOf(fullBox("meta", 0, 0, fullBox("iloc", 0, 0, u32(0))))
        assertGapKept(movie)
    }

    @Test
    fun keepsGapsWhenChunkSpansMayBeTooShort() {
        val audio = MovieTestTrack("soun", "sowt", listOf(listOf(ByteArray(1), ByteArray(1))), sizeBits = 0)
        assertGapKept(MovieBuilder(listOf(video(), audio)))
    }

    @Test
    fun aKeptGapDoesNotStopTelemetryBeingZeroed() {
        val auxiliary = fullBox("saio", 0, 0, u32(0))
        val built = MovieBuilder(listOf(video(stblExtras = listOf(auxiliary)), telemetry())).build()
        assertZeroed(harness.strip(built.bytes).first, built.chunkSpans[1])
    }

    // endregion Gaps

    private fun assertGapKept(movie: MovieBuilder) {
        movie.unreferencedMedia = secret
        val input = movie.build().bytes

        val (output, report) = harness.strip(input)
        assertTrue(output.indexOf(secret) >= 0)
        assertFalse(report.removed.any { it.label == "Unreferenced data" })
        assertTrue(report.warnings.contains(MetadataWarning(MetadataWarningKind.UnreferencedDataKept, listOf(secret.size.toString()))))
    }

    private fun assertRejected(input: ByteArray) {
        assertThrows(MalformedFileException::class.java) { harness.inspect(input) }
    }

    private fun assertKept(input: ByteArray,
                           output: ByteArray,
                           spans: List<FileSpan>) {
        for (span in spans) {
            assertArrayEquals(input.copyOfRange(span.start.toInt(), span.end.toInt()), output.copyOfRange(span.start.toInt(), span.end.toInt()))
        }
    }

    // x264's settings in the video's SEI user data become 0xFF; nothing else in a kept chunk changes.
    private fun assertKeptApartFromUserData(input: ByteArray,
                                            output: ByteArray,
                                            spans: List<FileSpan>) {
        for (span in spans) {
            assertTrue((span.start.toInt() until span.end.toInt()).all { input[it] == output[it] || output[it] == 0xFF.toByte() })
        }
    }

    private fun assertZeroed(output: ByteArray,
                             spans: List<FileSpan>) {
        assertTrue(spans.isNotEmpty())
        for (span in spans) {
            assertTrue(output.copyOfRange(span.start.toInt(), span.end.toInt()).all { it == 0.toByte() })
        }
    }

    private fun assertClean(output: ByteArray) {
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
        val (again, _: StripReport) = harness.strip(output)
        assertArrayEquals(output, again)
    }

    // The same bytes, relabelled as a telemetry track: real media, with a track ffmpeg can't write from scratch.
    private fun audioAsTelemetry(bytes: ByteArray): ByteArray {
        val patched = bytes.copyOf()
        patched.replaceFirst("soun", "meta")
        patched.replaceFirst("mp4a", "gpmd")
        return patched
    }

    private fun ByteArray.replaceFirst(from: String,
                                       to: String) {
        to.toByteArray().copyInto(this, indexOf(from.toByteArray()))
    }

    private fun tracks(bytes: ByteArray): List<Box> = findIn(bytes, "trak")

    // Fragmented fixtures have their samples in `trun`, so they're read the way the planner does.
    private fun sampleTableChunks(bytes: ByteArray): List<List<FileSpan>> {
        val reader = BinaryReader(ByteArrayChannel(bytes))
        val boxes = BoxReader(reader)
        val topLevel = boxes.readTopLevel().boxes
        val tables = SampleTableReader(reader)
        val fragments = MovieFragmentReader(reader, boxes)
        val runs = topLevel.filter { it.type == "moof" }.flatMap { fragments.read(it, emptyMap()) }
        return findIn(bytes, "stbl").mapIndexed { index, stbl ->
            tables.read(stbl).chunks.filter { it.length > 0 } + runs.filter { it.trackId == index + 1L }.flatMap { it.runs }
        }
    }

    private fun findIn(bytes: ByteArray,
                       type: String): List<Box> {
        val reader = BoxReader(BinaryReader(ByteArrayChannel(bytes)))
        val containers = setOf("moov", "trak", "mdia", "minf", "stbl")
        val found = mutableListOf<Box>()
        fun walk(boxes: List<Box>) {
            for (box in boxes) {
                if (box.type == type) {
                    found += box
                }
                if (box.type in containers) {
                    walk(reader.readChildren(box))
                }
            }
        }
        walk(reader.readTopLevel().boxes)
        return found
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        return (0..size - pattern.size).firstOrNull { start -> pattern.indices.all { this[start + it] == pattern[it] } } ?: -1
    }

    // endregion Methods
}
