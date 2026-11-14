package com.prateekmahendrakar.metadatawiper.metadata.nal

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.accessUnitDelimiter
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.annexB
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.bytes
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.cameraMdpm
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.mdpmUuid
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.sei
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.slice
import com.prateekmahendrakar.metadatawiper.metadata.nal.SeiTestData.userData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnexBNalSplitterTest {

    // region State

    // Pieces of this many stream bytes, each after a 4-byte gap, as packet headers would leave.
    private companion object {
        const val PIECE_SIZE = 7
        const val GAP = 4
        const val BASE = 1000L
    }

    // endregion State

    // region Methods

    @Test
    fun collectsSeiUnitsSplitAcrossPieces() {
        for (codec in VideoCodec.entries) {
            val unit = sei(codec, userData(mdpmUuid, cameraMdpm))
            val stream = annexB(accessUnitDelimiter(codec), unit, slice(codec, 0x44)) + bytes(0, 0, 1) + slice(codec, 0x45)
            val found = split(codec, stream)

            val single = found.single()
            assertArrayEquals(unit, single.bytes)
            val start = stream.indexOfUnit(unit)
            val expected = (start until start + unit.size).map { offsetOf(it) }
            assertEquals(expected, single.pieces.flatMap { it.start until it.end })
            assertTrue(single.pieces.size > 1)
        }
    }

    // A four-byte start code's leading zero belongs to it, and the unit never ends in a zero.
    @Test
    fun leavesStartCodeZerosOutOfTheUnit() {
        val unit = sei(VideoCodec.H264, userData(mdpmUuid, bytes(0x01)))
        val stream = concat(bytes(0, 0, 1), unit, bytes(0, 0, 0, 0, 1), slice(VideoCodec.H264, 0x44))
        assertArrayEquals(unit, split(VideoCodec.H264, stream).single().bytes)
    }

    @Test
    fun closesTheLastUnitAtTheEndOfTheStream() {
        val unit = sei(VideoCodec.Hevc, userData(mdpmUuid, bytes(0x01)))
        assertArrayEquals(unit, split(VideoCodec.Hevc, annexB(slice(VideoCodec.Hevc, 0x44), unit)).single().bytes)
    }

    @Test
    fun findsHevcSuffixSei() {
        val suffix = SeiTestData.unit(VideoCodec.Hevc, 40, userData(mdpmUuid, bytes(0x01)) + bytes(0x80))
        assertArrayEquals(suffix, split(VideoCodec.Hevc, annexB(slice(VideoCodec.Hevc, 0x44), suffix, slice(VideoCodec.Hevc, 0x45))).single().bytes)
    }

    @Test
    fun ignoresBytesBeforeTheFirstStartCode() {
        val unit = sei(VideoCodec.H264, userData(mdpmUuid, bytes(0x01)))
        assertEquals(1, split(VideoCodec.H264, bytes(0x06, 0x05, 0x10) + annexB(unit)).size)
    }

    @Test
    fun rejectsAnOversizedSeiUnit() {
        val stream = annexB(bytes(0x06) + ByteArray(LengthPrefixedNalScanner.MAX_SEI_SIZE + 1) { 0x11 })
        assertThrows(MalformedFileException::class.java) { split(VideoCodec.H264, stream) }
    }

    private fun split(codec: VideoCodec,
                      stream: ByteArray): List<NalUnit> {
        val found = mutableListOf<NalUnit>()
        val splitter = AnnexBNalSplitter(codec) { found += it }
        for (start in stream.indices step PIECE_SIZE) {
            splitter.feed(stream, start, minOf(start + PIECE_SIZE, stream.size), offsetOf(start))
        }
        splitter.finish()
        return found
    }

    private fun offsetOf(streamIndex: Int): Long = BASE + streamIndex + (streamIndex / PIECE_SIZE) * GAP

    private fun ByteArray.indexOfUnit(unit: ByteArray): Int {
        return (0..size - unit.size).first { start -> unit.indices.all { this[start + it] == unit[it] } }
    }

    // endregion Methods
}
