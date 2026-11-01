package com.prateekmahendrakar.metadatawiper.metadata.io

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream

class BlankedCopyTest {

    private val input = ByteArray(20) { (it + 1).toByte() }

    @Test
    fun replacesRangesAndCopiesTheRest() {
        val blanked = listOf(BlankedRange(FileSpan(2, 5)),
                             BlankedRange(FileSpan(10, 14), prefix = byteArrayOf(0x66, 0x72), fill = 0x20))
        val expected = input.copyOf().also {
            it.fill(0, 2, 5)
            it[10] = 0x66
            it[11] = 0x72
            it.fill(0x20, 12, 14)
        }
        assertArrayEquals(expected, copy(blanked))
    }

    @Test
    fun cutsAtOutputLengthAndDropsRangesPastIt() {
        val blanked = listOf(BlankedRange(FileSpan(1, 3)), BlankedRange(FileSpan(15, 18)))
        val expected = input.copyOf(12).also { it.fill(0, 1, 3) }
        assertArrayEquals(expected, copy(blanked, outputLength = 12))
    }

    @Test
    fun rejectsUnsortedOrStraddlingRanges() {
        val cases = mapOf("unsorted" to listOf(BlankedRange(FileSpan(8, 10)), BlankedRange(FileSpan(2, 4))),
                          "overlapping" to listOf(BlankedRange(FileSpan(2, 6)), BlankedRange(FileSpan(5, 8))),
                          "straddles the cut" to listOf(BlankedRange(FileSpan(10, 14))))
        for ((name, blanked) in cases) {
            assertThrows(name, IllegalArgumentException::class.java) { copy(blanked, outputLength = 12) }
        }
    }

    private fun copy(blanked: List<BlankedRange>,
                     outputLength: Long = input.size.toLong()): ByteArray {
        val output = ByteArrayOutputStream()
        BlankedCopy.write(BinaryReader(ByteArrayChannel(input)), output, blanked, outputLength)
        return output.toByteArray()
    }
}
