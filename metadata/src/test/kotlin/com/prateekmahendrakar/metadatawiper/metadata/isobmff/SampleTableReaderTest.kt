package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u64
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u8
import com.prateekmahendrakar.metadatawiper.metadata.movie.BuiltMovie
import com.prateekmahendrakar.metadatawiper.metadata.movie.MovieBuilder
import com.prateekmahendrakar.metadatawiper.metadata.movie.MovieTestTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleTableReaderTest {

    @Test
    fun readsInterleavedTracks() {
        val video = MovieTestTrack("vide", "avc1", listOf(samples(100, 20), samples(30), samples(40, 41, 42)))
        val telemetry = MovieTestTrack("meta", "gpmd", listOf(samples(7), samples(9)))
        val movie = MovieBuilder(listOf(video, telemetry)).build()

        for (track in 0..1) {
            val table = readTrack(movie, track)
            assertEquals(movie.chunkSpans[track], table.chunks)
            assertNull(table.constantSampleSize)
        }
        assertEquals(6L, readTrack(movie, 0).sampleCount)
    }

    @Test
    fun readsEveryOffsetAndSizeLayout() {
        val layouts = listOf(32 to false, 16 to true, 8 to false, 4 to true)
        for ((sizeBits, largeOffsets) in layouts) {
            // Odd sample counts leave a half-used byte at the end of a 4-bit table.
            val track = MovieTestTrack("meta", "camm", listOf(samples(3, 15, 1), samples(12), samples(5, 6)), sizeBits, largeOffsets)
            val movie = MovieBuilder(listOf(track)).apply { largeMediaData = largeOffsets }.build()
            assertEquals("$sizeBits-bit sizes", movie.chunkSpans[0], readTrack(movie, 0).chunks)
        }
    }

    @Test
    fun computesConstantSizeChunksWithoutATable() {
        val track = MovieTestTrack("soun", "sowt", listOf(samples(4, 4, 4, 4), samples(4, 4), samples(4, 4)), sizeBits = 0)
        val movie = MovieBuilder(listOf(track)).build()

        val table = readTrack(movie, 0)
        assertEquals(movie.chunkSpans[0], table.chunks)
        assertEquals(4L, table.constantSampleSize)
        assertEquals(8L, table.sampleCount)
    }

    @Test
    fun readsTrackWithNoSamples() {
        val table = read(stbl(stco(), stsc(), stsz(constantSize = 0, count = 0)))
        assertEquals(emptyList<FileSpan>(), table.chunks)
        assertEquals(0L, table.sampleCount)
    }

    @Test
    fun rejectsInconsistentTables() {
        val cases = mapOf("no chunk offsets" to stbl(stsc(1L to 1L), stsz(constantSize = 1, count = 1)),
                          "both offset boxes" to stbl(stco(0), co64(0), stsc(1L to 1L), stsz(constantSize = 1, count = 1)),
                          "no sizes" to stbl(stco(0), stsc(1L to 1L)),
                          "stsc not from chunk 1" to stbl(stco(0), stsc(2L to 1L), stsz(constantSize = 1, count = 1)),
                          "stsc out of order" to stbl(stco(0, 0), stsc(1L to 1L, 1L to 2L), stsz(constantSize = 1, count = 3)),
                          "more samples placed than sized" to stbl(stco(0, 0), stsc(1L to 2L), stsz(constantSize = 1, count = 3)),
                          "fewer samples placed than sized" to stbl(stco(0), stsc(1L to 1L), stsz(constantSize = 1, count = 3)),
                          "chunk past end of file" to stbl(stco(0), stsc(1L to 1L), stsz(constantSize = 10_000, count = 1)),
                          "offset past end of file" to stbl(stco(1_000_000), stsc(1L to 1L), stsz(constantSize = 1, count = 1)),
                          "size table cut short" to stbl(stco(0), stsc(1L to 2L), fullBox("stsz", 0, 0, u32(0), u32(2), u32(1))),
                          "offset table cut short" to stbl(fullBox("stco", 0, 0, u32(3), u32(0)), stsc(1L to 1L), stsz(constantSize = 1, count = 1)),
                          "12-bit compact sizes" to stbl(stco(0), stsc(1L to 1L), fullBox("stz2", 0, 0, ByteArray(3), u8(12), u32(1), u8(0), u8(0))))
        for ((name, bytes) in cases) {
            assertThrows(name, MalformedFileException::class.java) { read(bytes) }
        }
    }

    // Counts like these take a few bytes to declare, so they must fail fast rather than loop or allocate.
    @Test
    fun rejectsHugeCountsQuickly() {
        val cases = mapOf("4 billion constant-size samples" to stbl(stco(0), stsc(1L to 0xFFFFFFFFL), stsz(constantSize = 1, count = 0xFFFFFFFFL)),
                          "4 billion chunks" to stbl(fullBox("stco", 0, 0, u32(0xFFFFFFFFL)), stsc(1L to 1L), stsz(constantSize = 1, count = 1)),
                          "chunk size overflow" to stbl(stco(0), stsc(1L to 40_000_000L), stsz(constantSize = 0xFFFFFFFFL, count = 40_000_000L)))
        for ((name, bytes) in cases) {
            val started = System.nanoTime()
            assertThrows(name, MalformedFileException::class.java) { read(bytes) }
            assertTrue("$name took too long", System.nanoTime() - started < 1_000_000_000L)
        }
    }

    private fun readTrack(movie: BuiltMovie,
                          index: Int): SampleTable {
        val reader = BinaryReader(ByteArrayChannel(movie.bytes))
        val boxes = BoxReader(reader)
        val moov = boxes.read(0, movie.bytes.size.toLong()).single { it.type == "moov" }
        val trak = boxes.readChildren(moov).filter { it.type == "trak" }[index]
        val stbl = listOf("mdia", "minf", "stbl").fold(trak) { parent, type -> boxes.readChildren(parent).single { it.type == type } }
        return SampleTableReader(reader).read(stbl)
    }

    // The stbl sits at the start of a file padded to 64 bytes past it, so small offsets and sizes are inside the file.
    private fun read(stbl: ByteArray): SampleTable {
        val bytes = stbl + ByteArray(64)
        val reader = BinaryReader(ByteArrayChannel(bytes))
        val box = BoxReader(reader).read(0, stbl.size.toLong()).single()
        return SampleTableReader(reader).read(box)
    }

    private fun stbl(vararg children: ByteArray): ByteArray = box("stbl", *children)

    private fun stco(vararg offsets: Long): ByteArray = fullBox("stco", 0, 0, u32(offsets.size.toLong()), *offsets.map { u32(it) }.toTypedArray())

    private fun co64(vararg offsets: Long): ByteArray = fullBox("co64", 0, 0, u32(offsets.size.toLong()), *offsets.map { u64(it) }.toTypedArray())

    // (first chunk, samples per chunk)
    private fun stsc(vararg runs: Pair<Long, Long>): ByteArray {
        val entries = runs.map { (firstChunk, samples) -> concat(u32(firstChunk), u32(samples), u32(1)) }
        return fullBox("stsc", 0, 0, u32(runs.size.toLong()), *entries.toTypedArray())
    }

    private fun stsz(constantSize: Long,
                     count: Long): ByteArray = fullBox("stsz", 0, 0, u32(constantSize), u32(count))

    private fun samples(vararg sizes: Int): List<ByteArray> = sizes.map { size -> ByteArray(size) { (size + it).toByte() } }
}
