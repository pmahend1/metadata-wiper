package com.prateekmahendrakar.metadatawiper.metadata.io

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteOrder

class BinaryReaderTest {

    private val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte())

    @Test
    fun readsBigEndianByDefault() {
        val reader = reader(data)
        assertEquals(0x0102, reader.readU16())
        assertEquals(0x0304, reader.readU16())
        assertEquals(0xFFFFFFFEL, reader.readU32())
    }

    @Test
    fun readsLittleEndianWhenAsked() {
        val reader = reader(data).apply { byteOrder = ByteOrder.LITTLE_ENDIAN }
        assertEquals(0x04030201L, reader.readU32())
        assertEquals(-0x01000001, reader.readS32())
    }

    @Test
    fun readsUnsignedBytes() {
        val reader = reader(data)
        reader.seek(4)
        assertEquals(0xFF, reader.readU8())
    }

    @Test
    fun tracksPositionAcrossReadsSeeksAndSkips() {
        val reader = reader(data)
        reader.readU16()
        reader.skip(3)
        assertEquals(5, reader.position)
        assertEquals(3, reader.remaining)
        reader.seek(1)
        assertArrayEquals(byteArrayOf(0x02, 0x03), reader.readBytes(2))
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsReadPastEnd() {
        reader(data).apply { seek(6) }.readU32()
    }

    // A corrupt length field must fail before the buffer is allocated.
    @Test(expected = MalformedFileException::class)
    fun rejectsHugeLengthWithoutAllocating() {
        reader(data).readBytes(Int.MAX_VALUE)
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsSeekPastEnd() {
        reader(data).seek(data.size + 1L)
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsNegativeSkip() {
        reader(data).skip(-1)
    }

    @Test(expected = MalformedFileException::class)
    fun rejectsU64AboveLongRange() {
        reader(data + data).apply { seek(4) }.readU64()
    }

    private fun reader(bytes: ByteArray): BinaryReader = BinaryReader(ByteArrayChannel(bytes))
}
