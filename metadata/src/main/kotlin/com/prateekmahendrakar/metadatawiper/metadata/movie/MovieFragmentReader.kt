package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import java.nio.ByteBuffer

/*
 * Resolves `moof/traf/trun` to the file spans of each track's samples, which fragmented files
 * keep out of the sample tables. A run's samples are contiguous, so one span covers it. Sizes are
 * summed from the `trun` table in blocks, and a run with no table is computed, so a run costs at
 * most one pass over its own box. A fragment that can't be resolved fails the file: telemetry
 * and gap zeroing both depend on knowing exactly where the samples are.
 */
class MovieFragmentReader(private val reader: BinaryReader,
                          private val boxReader: BoxReader) {

    // region State

    private var runCount = 0L

    private companion object {
        const val TRAF = "traf"
        const val TFHD = "tfhd"
        const val TRUN = "trun"
        const val TREX = "trex"

        const val FLAGS_MASK = 0xFFFFFFL

        const val BASE_DATA_OFFSET_PRESENT = 0x1L
        const val SAMPLE_DESCRIPTION_PRESENT = 0x2L
        const val DEFAULT_DURATION_PRESENT = 0x8L
        const val DEFAULT_SIZE_PRESENT = 0x10L
        const val DEFAULT_BASE_IS_MOOF = 0x20000L

        const val DATA_OFFSET_PRESENT = 0x1L
        const val FIRST_SAMPLE_FLAGS_PRESENT = 0x4L
        const val SAMPLE_DURATION_PRESENT = 0x100L
        const val SAMPLE_SIZE_PRESENT = 0x200L
        const val SAMPLE_FLAGS_PRESENT = 0x400L
        const val SAMPLE_COMPOSITION_PRESENT = 0x800L

        const val FIELD_SIZE = 4L
        const val LARGE_FIELD_SIZE = 8L
        const val TREX_SIZE = 20L
        const val TABLE_BLOCK_SIZE = 64 * 1024

        // Every run becomes a FileSpan, so this bounds memory: eleven days of one fragment a second per track.
        const val MAX_RUNS = 1_000_000L
    }

    // endregion State

    // region Methods

    fun readTrackExtends(mvex: Box): List<TrackExtends> {
        return boxReader.readChildren(mvex).filter { it.type == TREX }.map { trex ->
            val start = boxReader.fullBoxDataStart(trex)
            if (trex.end - start < TREX_SIZE) {
                throw MalformedFileException("'trex' box at offset ${trex.start} is too short")
            }
            reader.seek(start)
            val trackId = reader.readU32()
            reader.skip(FIELD_SIZE * 2)
            TrackExtends(trex, trackId, reader.readU32())
        }
    }

    fun read(moof: Box,
             defaultSampleSizes: Map<Long, Long>): List<TrackFragment> {
        val fragments = mutableListOf<TrackFragment>()
        var previousEnd = moof.start
        for (traf in boxReader.readChildren(moof).filter { it.type == TRAF }) {
            val children = boxReader.readChildren(traf)
            val tfhd = children.firstOrNull { it.type == TFHD }
                       ?: throw MalformedFileException("'traf' box at offset ${traf.start} has no 'tfhd' box")
            val header = readHeader(tfhd)
            val base = header.baseDataOffset ?: if (header.baseIsMoof) moof.start else previousEnd
            val defaultSize = header.defaultSampleSize ?: defaultSampleSizes[header.trackId]

            var position = base
            val runs = children.filter { it.type == TRUN }.map { trun ->
                readRun(trun, base, position, defaultSize).also { position = it.end }
            }
            val followsPrevious = header.baseDataOffset == null && header.baseIsMoof == false && fragments.isNotEmpty()
            fragments += TrackFragment(traf, header.trackId, runs, followsPrevious)
            previousEnd = position
        }
        return fragments
    }

    private fun flags(box: Box): Long {
        boxReader.fullBoxDataStart(box)
        reader.seek(box.dataStart)
        return reader.readU32() and FLAGS_MASK
    }

    private fun readHeader(tfhd: Box): FragmentHeader {
        val flags = flags(tfhd)
        val hasBaseOffset = flags and BASE_DATA_OFFSET_PRESENT != 0L
        val fieldsSize = FIELD_SIZE +
                         (if (hasBaseOffset) LARGE_FIELD_SIZE else 0L) +
                         listOf(SAMPLE_DESCRIPTION_PRESENT, DEFAULT_DURATION_PRESENT, DEFAULT_SIZE_PRESENT).count { flags and it != 0L } * FIELD_SIZE
        if (tfhd.end - reader.position < fieldsSize) {
            throw MalformedFileException("'tfhd' box at offset ${tfhd.start} is too short")
        }

        val trackId = reader.readU32()
        val baseDataOffset = if (hasBaseOffset) reader.readU64() else null
        if (flags and SAMPLE_DESCRIPTION_PRESENT != 0L) {
            reader.skip(FIELD_SIZE)
        }
        if (flags and DEFAULT_DURATION_PRESENT != 0L) {
            reader.skip(FIELD_SIZE)
        }
        val defaultSize = if (flags and DEFAULT_SIZE_PRESENT != 0L) reader.readU32() else null
        return FragmentHeader(trackId, baseDataOffset, defaultSize, flags and DEFAULT_BASE_IS_MOOF != 0L)
    }

    // A run with no data offset carries on from where the previous one ended.
    private fun readRun(trun: Box,
                        base: Long,
                        position: Long,
                        defaultSize: Long?): FileSpan {
        runCount++
        if (runCount > MAX_RUNS) {
            throw MalformedFileException("More than $MAX_RUNS fragment runs")
        }

        val flags = flags(trun)
        val hasDataOffset = flags and DATA_OFFSET_PRESENT != 0L
        val hasFirstFlags = flags and FIRST_SAMPLE_FLAGS_PRESENT != 0L
        val fieldsSize = FIELD_SIZE * (1 + listOf(hasDataOffset, hasFirstFlags).count { it })
        if (trun.end - reader.position < fieldsSize) {
            throw MalformedFileException("'trun' box at offset ${trun.start} is too short")
        }

        val sampleCount = reader.readU32()
        val dataOffset = if (hasDataOffset) reader.readS32().toLong() else null
        if (hasFirstFlags) {
            reader.skip(FIELD_SIZE)
        }

        val recordFields = listOf(SAMPLE_DURATION_PRESENT, SAMPLE_SIZE_PRESENT, SAMPLE_FLAGS_PRESENT, SAMPLE_COMPOSITION_PRESENT)
        val recordSize = recordFields.count { flags and it != 0L } * FIELD_SIZE.toInt()
        if (sampleCount * recordSize > trun.end - reader.position) {
            throw MalformedFileException("'trun' box at offset ${trun.start} is too short for its table")
        }

        val length = if (flags and SAMPLE_SIZE_PRESENT != 0L) {
            val sizeOffset = if (flags and SAMPLE_DURATION_PRESENT != 0L) FIELD_SIZE.toInt() else 0
            sumSampleSizes(sampleCount, recordSize, sizeOffset)
        } else {
            constantSizeLength(trun, sampleCount, defaultSize)
        }

        val start = if (dataOffset == null) position else base + dataOffset
        if (start < 0 || start > reader.size || length > reader.size - start) {
            throw MalformedFileException("Fragment run at offset ${trun.start} points outside the file")
        }
        return FileSpan(start, start + length)
    }

    private fun constantSizeLength(trun: Box,
                                   sampleCount: Long,
                                   defaultSize: Long?): Long {
        if (sampleCount == 0L) {
            return 0L
        }
        if (defaultSize == null) {
            throw MalformedFileException("Fragment run at offset ${trun.start} has no sample size")
        }
        if (defaultSize > reader.size / sampleCount) {
            throw MalformedFileException("Fragment run at offset ${trun.start} is larger than the file")
        }
        return sampleCount * defaultSize
    }

    private fun sumSampleSizes(sampleCount: Long,
                               recordSize: Int,
                               sizeOffset: Int): Long {
        val blockRecords = TABLE_BLOCK_SIZE / recordSize
        var left = sampleCount
        var total = 0L
        while (left > 0) {
            val records = minOf(left, blockRecords.toLong()).toInt()
            val block = ByteBuffer.wrap(reader.readBytes(records * recordSize))
            for (index in 0 until records) {
                total += block.getInt(index * recordSize + sizeOffset).toLong() and 0xFFFFFFFFL
            }
            if (total > reader.size) {
                throw MalformedFileException("A fragment run's samples add up to more than the file")
            }
            left -= records
        }
        return total
    }

    // endregion Methods

    private class FragmentHeader(val trackId: Long,
                                 val baseDataOffset: Long?,
                                 val defaultSampleSize: Long?,
                                 val baseIsMoof: Boolean)
}
