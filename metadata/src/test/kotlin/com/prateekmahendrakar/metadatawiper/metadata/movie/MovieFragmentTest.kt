package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieFragmentTest {

    // region State

    private val harness = HandlerHarness(IsoMediaHandler(FileFormat.Mp4))

    private val secret = "48.8582N".toByteArray()

    private val frame = ByteArray(32) { 7 }

    private val videoId = 1L
    private val telemetryId = 2L

    private val noChunks = emptyList<List<ByteArray>>()

    private val tracks = listOf(MovieTestTrack("vide", "avc1", noChunks), MovieTestTrack("meta", "gpmd", noChunks))

    private companion object {
        const val BASE_OFFSET = 0x1
        const val DEFAULT_SIZE = 0x10
        const val BASE_IS_MOOF = 0x20000

        const val DATA_OFFSET = 0x1
        const val SAMPLE_DURATION = 0x100
        const val SAMPLE_SIZE = 0x200
    }

    // endregion State

    // region Methods

    private fun trex(trackId: Long,
                     defaultSize: Long = 0): ByteArray = fullBox("trex", 0, 0, u32(trackId), u32(1), u32(0), u32(defaultSize), u32(0))

    private fun trun(sizes: List<Int>,
                     dataOffset: Long): ByteArray {
        return fullBox("trun", 0, DATA_OFFSET or SAMPLE_SIZE, u32(sizes.size.toLong()), u32(dataOffset), *sizes.map { u32(it.toLong()) }.toTypedArray())
    }

    // A `moof` and the `mdat` after it, holding one track's samples per entry, located relative to the `moof`.
    private fun fragment(vararg samples: Pair<Long, ByteArray>): ByteArray {
        fun moof(dataStart: Long): ByteArray {
            var position = dataStart
            val trafs = samples.map { (trackId, data) ->
                val traf = box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(trackId)), trun(listOf(data.size), position))
                position += data.size
                traf
            }
            return box("moof", fullBox("mfhd", 0, 0, u32(1)), *trafs.toTypedArray())
        }
        val mediaHeaderSize = 8L
        return moof(moof(0).size + mediaHeaderSize) + box("mdat", *samples.map { it.second }.toTypedArray())
    }

    private fun fragmented(vararg fragments: ByteArray): MovieBuilder {
        val movie = MovieBuilder(tracks)
        movie.moovExtras = listOf(box("mvex", trex(videoId), trex(telemetryId)))
        movie.topLevelExtras = fragments.toList()
        return movie
    }

    private fun assertRejected(input: ByteArray) {
        assertThrows(MalformedFileException::class.java) { harness.inspect(input) }
    }

    private fun moofRuns(input: ByteArray,
                         defaults: Map<Long, Long> = emptyMap()): List<TrackFragment> {
        val reader = BinaryReader(ByteArrayChannel(input))
        val boxes = BoxReader(reader)
        return boxes.readTopLevel().boxes.filter { it.type == "moof" }.flatMap { MovieFragmentReader(reader, boxes).read(it, defaults) }
    }

    @Test
    fun zeroesTelemetrySamplesInFragments() {
        val input = fragmented(fragment(videoId to frame, telemetryId to secret), fragment(telemetryId to secret, videoId to frame)).build().bytes

        val (output, report) = harness.strip(input)
        assertTrue(report.removed.contains(MetadataItem(MetadataCategory.Location, "Telemetry track", "gpmd", 16)))
        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf(secret))
        assertEquals(2, output.occurrences(frame))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
        assertArrayEquals(output, harness.strip(output).first)
    }

    @Test
    fun freesTheFragmentsAndDefaultsOfRemovedTracks() {
        val input = fragmented(fragment(videoId to frame, telemetryId to secret)).build().bytes
        val output = harness.strip(input).first

        val reader = BoxReader(BinaryReader(ByteArrayChannel(output)))
        val topLevel = reader.readTopLevel().boxes
        val moof = reader.readChildren(topLevel.single { it.type == "moof" })
        assertEquals(listOf("mfhd", "traf", "free"), moof.map { it.type })
        val moov = reader.readChildren(topLevel.single { it.type == "moov" })
        assertEquals(listOf("trex", "free"), reader.readChildren(moov.single { it.type == "mvex" }).map { it.type })
    }

    @Test
    fun zeroesBytesNoFragmentReferences() {
        val stale = fragment(videoId to frame) + box("mdat", secret)
        val input = fragmented(stale).build().bytes

        val (output, report) = harness.strip(input)
        assertTrue(report.removed.any { it.label == "Unreferenced data" })
        assertEquals(-1, output.indexOf(secret))
        assertTrue(output.indexOf(frame) >= 0)
    }

    @Test
    fun readsDefaultSizesFromTheFragmentAndTheMovie() {
        val samples = fullBox("trun", 0, DATA_OFFSET, u32(2), u32(100))
        val fromFragment = box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF or DEFAULT_SIZE, u32(videoId), u32(8)), samples))
        val fromMovie = box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), samples))
        val padded = ByteArray(256)

        assertEquals(listOf(FileSpan(100, 116)), moofRuns(fromFragment + padded).single().runs)
        assertEquals(listOf(FileSpan(100, 112)), moofRuns(fromMovie + padded, mapOf(videoId to 6L)).single().runs)

        val noDefaults = MovieBuilder(tracks)
        noDefaults.topLevelExtras = listOf(fromMovie, box("mdat", padded))
        assertRejected(noDefaults.build().bytes)
    }

    @Test
    fun readsSampleSizesAfterOtherPerSampleFields() {
        val records = concat(u32(1000), u32(8), u32(1000), u32(4))
        val samples = fullBox("trun", 0, DATA_OFFSET or SAMPLE_DURATION or SAMPLE_SIZE, u32(2), u32(100), records)
        val input = box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), samples)) + ByteArray(256)
        assertEquals(listOf(FileSpan(100, 112)), moofRuns(input).single().runs)
    }

    @Test
    fun usesAnExplicitBaseOffset() {
        val samples = fullBox("trun", 0, DATA_OFFSET or SAMPLE_SIZE, u32(1), u32(10), u32(4))
        val input = box("moof", box("traf", fullBox("tfhd", 0, BASE_OFFSET, u32(videoId), u64(100)), samples)) + ByteArray(256)
        assertEquals(listOf(FileSpan(110, 114)), moofRuns(input).single().runs)
    }

    @Test
    fun aRunWithoutADataOffsetFollowsThePreviousOne() {
        val first = trun(listOf(8), 100)
        val second = fullBox("trun", 0, SAMPLE_SIZE, u32(1), u32(4))
        val input = box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), first, second)) + ByteArray(256)
        assertEquals(listOf(FileSpan(100, 108), FileSpan(108, 112)), moofRuns(input).single().runs)
    }

    @Test
    fun aFragmentWithoutABaseFollowsThePreviousFragment() {
        val first = box("traf", fullBox("tfhd", 0, 0, u32(videoId)), trun(listOf(8), 100))
        val second = box("traf", fullBox("tfhd", 0, 0, u32(telemetryId)), trun(listOf(4), 2))
        val fragments = moofRuns(box("moof", first, second) + ByteArray(256))

        assertEquals(listOf(FileSpan(100, 108)), fragments[0].runs)
        assertEquals(listOf(FileSpan(110, 114)), fragments[1].runs)
        assertEquals(listOf(false, true), fragments.map { it.followsPreviousFragment })
    }

    // Freeing the removed fragment would move where a reader looks for the kept one's samples.
    @Test
    fun refusesToRemoveAFragmentAnotherOneCountsFrom() {
        val removed = box("traf", fullBox("tfhd", 0, 0, u32(telemetryId)), trun(listOf(8), 100))
        val kept = box("traf", fullBox("tfhd", 0, 0, u32(videoId)), trun(listOf(4), 0))
        val input = fragmented(box("moof", removed, kept), box("mdat", ByteArray(256))).build().bytes

        val failure = assertThrows(UnsupportedFileException::class.java) { harness.inspect(input) }
        assertEquals(UnsupportedReason.NotYetSupported, failure.reason)
    }

    @Test
    fun rejectsFragmentsOfTracksTheMovieDoesNotHave() {
        assertRejected(fragmented(fragment(9L to frame)).build().bytes)
    }

    @Test
    fun rejectsRunsOutsideTheFile() {
        val movie = fragmented(box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), trun(listOf(64), 1_000_000))), box("mdat", ByteArray(8)))
        assertRejected(movie.build().bytes)
        val before = fragmented(box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), trun(listOf(4), -1_000_000))), box("mdat", ByteArray(8)))
        assertRejected(before.build().bytes)
    }

    @Test
    fun rejectsDamagedFragmentBoxes() {
        val noHeader = box("moof", box("traf", trun(listOf(4), 8)))
        val shortRun = box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), fullBox("trun", 0, DATA_OFFSET, u32(1))))
        for (moof in listOf(noHeader, shortRun)) {
            assertRejected(fragmented(moof, box("mdat", ByteArray(8))).build().bytes)
        }
    }

    // Billions of samples described in a few bytes mustn't be walked or overflow a length.
    @Test
    fun rejectsImpossibleSampleCountsWithoutWalkingThem() {
        val count = u32(0xFFFFFFFFL)
        val constant = fullBox("trun", 0, DATA_OFFSET, count, u32(0))
        val tabled = fullBox("trun", 0, DATA_OFFSET or SAMPLE_SIZE, count, u32(0))
        val header = fullBox("tfhd", 0, BASE_IS_MOOF or DEFAULT_SIZE, u32(videoId), u32(0xFFFFFFFFL))
        for (run in listOf(constant, tabled)) {
            assertRejected(fragmented(box("moof", box("traf", header, run)), box("mdat", ByteArray(8))).build().bytes)
        }
    }

    @Test
    fun cutsTrailersThatNoFragmentReaches() {
        val movie = fragmented(fragment(videoId to frame))
        movie.trailing = ByteArray(4) + byteArrayOf(1, 2, 3, 4) + secret
        val input = movie.build().bytes

        val (output, report) = harness.strip(input)
        assertEquals(input.size - movie.trailing.size, output.size)
        assertTrue(report.removed.any { it.label == "Embedded data" })
    }

    @Test
    fun rejectsFragmentsThatRunIntoATrailer() {
        val trailing = ByteArray(4) + byteArrayOf(1, 2, 3, 4) + ByteArray(64)
        val moof = box("moof", box("traf", fullBox("tfhd", 0, BASE_IS_MOOF, u32(videoId)), trun(listOf(64), 76)))
        val movie = fragmented(moof, box("mdat", ByteArray(4)))
        movie.trailing = trailing
        assertRejected(movie.build().bytes)
    }

    @Test
    fun cutsRemovedBoxesAfterTheLastFragment() {
        val movie = fragmented(fragment(videoId to frame), box("uuid", ByteArray(16) { 2 }, secret))
        val input = movie.build().bytes
        val output = harness.strip(input).first
        assertEquals(input.size - movie.topLevelExtras.last().size, output.size)
    }

    @Test
    fun zeroesProducerTimes() {
        val ntpTime = ByteArray(8) { 0x5A }
        val producerTime = fullBox("prft", 0, 0, u32(videoId), ntpTime, u32(0))
        val input = fragmented(producerTime, fragment(videoId to frame)).build().bytes

        val (output, report) = harness.strip(input)
        harness.assertHas(report.removed, MetadataCategory.DateTime, "Timestamp")
        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf(ntpTime))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
    }

    @Test
    fun rejectsShortProducerTimes() {
        assertRejected(fragmented(fullBox("prft", 0, 0, u32(videoId))).build().bytes)
    }

    private fun ByteArray.occurrences(pattern: ByteArray): Int {
        return (0..size - pattern.size).count { start -> pattern.indices.all { this[start + it] == pattern[it] } }
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        return (0..size - pattern.size).firstOrNull { start -> pattern.indices.all { this[start + it] == pattern[it] } } ?: -1
    }

    // endregion Methods
}
