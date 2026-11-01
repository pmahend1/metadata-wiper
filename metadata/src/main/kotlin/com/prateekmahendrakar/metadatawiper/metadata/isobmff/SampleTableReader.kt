package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Turns a track's sample table (`stbl`) into chunk spans: `stco`/`co64` give each chunk's
 * offset, `stsc` how many samples it holds, and `stsz`/`stz2` how big they are. A constant
 * sample size describes billions of samples in a few bytes, so that case is computed per chunk,
 * never per sample, and every count is capped before anything is allocated.
 */
class SampleTableReader(private val reader: BinaryReader) {

    // region State

    private val boxReader = BoxReader(reader)

    private companion object {
        /*
         * Samples cost no memory, so their cap only bounds work: about ten days of 60 fps video.
         * Every chunk becomes a FileSpan, so that cap bounds memory (~40 MB): nine hours of
         * 30 fps video stored one frame per chunk, the least efficient layout writers use.
         */
        const val MAX_SAMPLES = 50_000_000L
        const val MAX_CHUNKS = 1_000_000L

        const val CHUNK_OFFSET = "stco"
        const val LARGE_CHUNK_OFFSET = "co64"
        const val SAMPLE_TO_CHUNK = "stsc"
        const val SAMPLE_SIZE = "stsz"
        const val COMPACT_SAMPLE_SIZE = "stz2"

        const val STSC_ENTRY_SIZE = 12L
        val compactFieldSizes = setOf(4, 8, 16)
        const val TABLE_BLOCK_SIZE = 64 * 1024
    }

    // endregion State

    // region Methods

    fun read(stbl: Box): SampleTable {
        val children = boxReader.readChildren(stbl)
        val offsets = readChunkOffsets(single(children, stbl, CHUNK_OFFSET, LARGE_CHUNK_OFFSET))
        val runs = readSampleToChunk(single(children, stbl, SAMPLE_TO_CHUNK))
        val sizes = readSampleSizes(single(children, stbl, SAMPLE_SIZE, COMPACT_SAMPLE_SIZE))

        val chunks = ArrayList<FileSpan>(offsets.size)
        var samples = 0L
        var run = 0
        for (chunk in offsets.indices) {
            // stsc numbers chunks from 1; a run lasts until the next one's first chunk.
            while (run + 1 < runs.size && runs[run + 1].firstChunk <= chunk + 1) {
                run++
            }
            val samplesInChunk = if (runs.isEmpty()) 0L else runs[run].samplesPerChunk
            samples += samplesInChunk
            if (samples > sizes.count) {
                throw MalformedFileException("Sample-to-chunk table at ${stbl.start} lists more samples than the size table (${sizes.count})")
            }
            val length = sizes.next(samplesInChunk, reader.size)
            if (length > reader.size - offsets[chunk]) {
                throw MalformedFileException("Chunk at offset ${offsets[chunk]} runs past the end of the file")
            }
            chunks += FileSpan(offsets[chunk], offsets[chunk] + length)
        }
        if (samples != sizes.count) {
            throw MalformedFileException("Sample table at ${stbl.start} places $samples of ${sizes.count} samples")
        }
        return SampleTable(chunks, sizes.count, sizes.constantSize)
    }

    private fun single(children: List<Box>,
                       stbl: Box,
                       vararg types: String): Box {
        val matches = children.filter { it.type in types }
        if (matches.size != 1) {
            throw MalformedFileException("Sample table at ${stbl.start} needs exactly one of ${types.joinToString("/")}, found ${matches.size}")
        }
        return matches.single()
    }

    private fun readChunkOffsets(box: Box): LongArray {
        val entrySize = if (box.type == LARGE_CHUNK_OFFSET) 8 else 4
        val count = readEntryCount(box, entrySize.toLong(), MAX_CHUNKS)
        val offsets = LongArray(count.toInt())
        val table = readTable(entrySize * count)
        for (index in offsets.indices) {
            offsets[index] = if (entrySize == 8) table.long else table.int.toLong() and 0xFFFFFFFFL
            if (offsets[index] < 0 || offsets[index] > reader.size) {
                throw MalformedFileException("Chunk offset ${offsets[index]} is outside the file")
            }
        }
        return offsets
    }

    private fun readSampleToChunk(box: Box): List<ChunkRun> {
        val count = readEntryCount(box, STSC_ENTRY_SIZE, MAX_CHUNKS)
        val table = readTable(STSC_ENTRY_SIZE * count)
        val runs = ArrayList<ChunkRun>(count.toInt())
        for (index in 0 until count) {
            val firstChunk = table.int.toLong() and 0xFFFFFFFFL
            val samplesPerChunk = table.int.toLong() and 0xFFFFFFFFL
            table.int // sample description index
            val previous = runs.lastOrNull()?.firstChunk ?: 0L
            if (firstChunk <= previous || (runs.isEmpty() && firstChunk != 1L)) {
                throw MalformedFileException("Sample-to-chunk entries at ${box.start} aren't in order from chunk 1")
            }
            runs += ChunkRun(firstChunk, samplesPerChunk)
        }
        return runs
    }

    private fun readSampleSizes(box: Box): SampleSizes {
        reader.seek(boxReader.fullBoxDataStart(box))
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        if (box.type == SAMPLE_SIZE) {
            val constantSize = reader.readU32()
            val count = reader.readU32()
            requireSampleCount(count, box)
            if (constantSize != 0L) {
                return SampleSizes(count, constantSize, fieldBits = 0, reader.position)
            }
            requireTable(box, count * 32 / 8)
            return SampleSizes(count, constantSize = null, fieldBits = 32, reader.position)
        }

        reader.skip(3)
        val fieldBits = reader.readU8()
        if (fieldBits !in compactFieldSizes) {
            throw MalformedFileException("Compact sample sizes at ${box.start} use $fieldBits-bit fields")
        }
        val count = reader.readU32()
        requireSampleCount(count, box)
        requireTable(box, (count * fieldBits + 7) / 8)
        return SampleSizes(count, constantSize = null, fieldBits, reader.position)
    }

    private fun readEntryCount(box: Box,
                               entrySize: Long,
                               max: Long): Long {
        reader.seek(boxReader.fullBoxDataStart(box))
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        val count = reader.readU32()
        if (count > max) {
            throw MalformedFileException("'${box.type}' at ${box.start} lists $count entries")
        }
        requireTable(box, count * entrySize)
        return count
    }

    private fun requireSampleCount(count: Long,
                                   box: Box) {
        if (count > MAX_SAMPLES) {
            throw MalformedFileException("'${box.type}' at ${box.start} lists $count samples")
        }
    }

    private fun requireTable(box: Box,
                             byteCount: Long) {
        if (byteCount > box.end - reader.position) {
            throw MalformedFileException("'${box.type}' at ${box.start} is too short for its table")
        }
    }

    // Tables already in memory are bounded by MAX_CHUNKS entries; only sample sizes are streamed.
    private fun readTable(byteCount: Long): ByteBuffer = ByteBuffer.wrap(reader.readBytes(byteCount.toInt()))

    // endregion Methods

    private class ChunkRun(val firstChunk: Long,
                           val samplesPerChunk: Long)

    /*
     * Hands out sample sizes in order, a chunk at a time. Variable sizes are read from the table
     * in blocks, so a long track costs one read per block rather than one per sample. Nothing
     * else reads from the channel while a sample table is being walked.
     */
    private inner class SampleSizes(val count: Long,
                                    val constantSize: Long?,
                                    private val fieldBits: Int,
                                    private var tablePosition: Long) {

        private var block = ByteBuffer.allocate(0)
        private var pendingNibble = -1

        fun next(samples: Long,
                 limit: Long): Long {
            if (constantSize != null) {
                if (samples > 0 && constantSize > limit / samples) {
                    throw MalformedFileException("A chunk of $samples samples of $constantSize bytes is larger than the file")
                }
                return samples * constantSize
            }
            var total = 0L
            for (sample in 0 until samples) {
                total += nextSize()
                if (total > limit) {
                    throw MalformedFileException("A chunk's samples add up to more than the file")
                }
            }
            return total
        }

        private fun nextSize(): Long {
            return when (fieldBits) {
                32 -> nextBytes(4).int.toLong() and 0xFFFFFFFFL
                16 -> nextBytes(2).short.toLong() and 0xFFFFL
                8 -> nextBytes(1).get().toLong() and 0xFFL
                else -> nextNibble().toLong()
            }
        }

        // stz2 with 4-bit fields packs two samples per byte, high nibble first.
        private fun nextNibble(): Int {
            if (pendingNibble >= 0) {
                val low = pendingNibble
                pendingNibble = -1
                return low
            }
            val value = nextBytes(1).get().toInt() and 0xFF
            pendingNibble = value and 0x0F
            return value shr 4
        }

        private fun nextBytes(count: Int): ByteBuffer {
            if (block.remaining() < count) {
                refill()
            }
            return block
        }

        private fun refill() {
            val leftover = ByteArray(block.remaining()).also { block.get(it) }
            reader.seek(tablePosition)
            val fresh = reader.readBytes(minOf(TABLE_BLOCK_SIZE.toLong(), reader.size - tablePosition).toInt())
            tablePosition += fresh.size
            block = ByteBuffer.wrap(leftover + fresh).order(ByteOrder.BIG_ENDIAN)
        }
    }
}
