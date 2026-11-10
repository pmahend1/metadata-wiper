package com.prateekmahendrakar.metadatawiper.wipe

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

class ProgressOutputStreamTest {

    // region Methods

    @Test
    fun passesBytesThrough() {
        val output = ByteArrayOutputStream()
        ProgressOutputStream(output, 3, { false }, { }).use {
            it.write(1)
            it.write(byteArrayOf(9, 2, 3, 9), 1, 2)
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test
    fun reportsCountedBytesAgainstTheTotal() {
        val fractions = mutableListOf<Float>()
        val stream = ProgressOutputStream(OutputStream.nullOutputStream(), 200, { false }, { fractions += it })
        stream.write(ByteArray(50))
        stream.write(ByteArray(150))
        assertEquals(listOf(0.25f, 1f), fractions)
    }

    @Test
    fun reportsOncePerPercent() {
        val fractions = mutableListOf<Float>()
        val stream = ProgressOutputStream(OutputStream.nullOutputStream(), 1_000_000, { false }, { fractions += it })
        repeat(1_000) { stream.write(ByteArray(1_000)) }
        assertEquals(100, fractions.size)
    }

    @Test
    fun neverReportsPastTheEndWhenOutputOutgrowsTheEstimate() {
        val fractions = mutableListOf<Float>()
        val stream = ProgressOutputStream(OutputStream.nullOutputStream(), 10, { false }, { fractions += it })
        stream.write(ByteArray(40))
        assertTrue(fractions.all { it <= 1f })
    }

    @Test
    fun reportsNothingWithoutAKnownTotal() {
        val fractions = mutableListOf<Float>()
        ProgressOutputStream(OutputStream.nullOutputStream(), 0, { false }, { fractions += it }).write(ByteArray(10))
        assertTrue(fractions.isEmpty())
    }

    @Test
    fun throwsOnTheFirstWriteAfterCancellationWithoutWriting() {
        var isCancelled = false
        val output = ByteArrayOutputStream()
        val stream = ProgressOutputStream(output, 10, { isCancelled }, { })
        stream.write(ByteArray(4))
        isCancelled = true
        assertThrows(CancellationException::class.java) { stream.write(ByteArray(4)) }
        assertThrows(CancellationException::class.java) { stream.write(1) }
        assertEquals(4, output.size())
    }

    // endregion Methods
}
