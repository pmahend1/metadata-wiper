package com.prateekmahendrakar.metadatawiper.metadata.ebml

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EbmlReaderTest {

    // region Methods

    @Test
    fun readsIdsSizesAndValues() {
        val bytes = EbmlBuilder.master(EbmlId.INFO,
                                       EbmlBuilder.unsigned(EbmlId.TIMESTAMP_SCALE, 1_000_000),
                                       EbmlBuilder.text(EbmlId.TITLE, "Clip\u0000\u0000"))
        val reader = reader(bytes)
        val info = reader.readElement(0, bytes.size.toLong(), 0)
        val children = reader.children(info)

        assertEquals(EbmlId.INFO, info.id)
        assertEquals(bytes.size.toLong(), info.end)
        assertEquals(listOf(EbmlId.TIMESTAMP_SCALE, EbmlId.TITLE), children.map { it.id })
        assertEquals(1_000_000L, reader.readUnsigned(children[0]))
        assertEquals("Clip", reader.readText(children[1]))
        assertEquals(1, children[0].depth)
    }

    @Test
    fun unknownSizeClusterEndsAtTheNextSegmentChild() {
        val cluster = EbmlBuilder.unknownSize(EbmlId.CLUSTER,
                                              EbmlBuilder.unsigned(0xE7L, 0),
                                              EbmlBuilder.element(EbmlId.SIMPLE_BLOCK, ByteArray(10)))
        val cues = EbmlBuilder.master(EbmlId.CUES)
        val segment = EbmlBuilder.unknownSize(EbmlId.SEGMENT, cluster, cues)
        val reader = reader(segment)

        val element = reader.readElement(0, segment.size.toLong(), 0)
        val children = reader.children(element)

        assertEquals(segment.size.toLong(), element.end)
        assertEquals(listOf(EbmlId.CLUSTER, EbmlId.CUES), children.map { it.id })
        assertEquals(element.dataStart + cluster.size, children[0].end)
    }

    @Test
    fun unknownSizeIsOnlyAllowedForSegmentAndCluster() {
        val bytes = EbmlBuilder.unknownSize(EbmlId.TAGS, EbmlBuilder.master(EbmlId.TAG))
        assertThrows(MalformedFileException::class.java) { reader(bytes).readElement(0, bytes.size.toLong(), 0) }
    }

    @Test
    fun readHeaderReportsUnknownSizeAsNull() {
        val bytes = EbmlBuilder.unknownSize(EbmlId.SEGMENT)
        assertNull(reader(bytes).readHeader(0, bytes.size.toLong()).size)
    }

    @Test
    fun rejectsMalformedElements() {
        val fiveByteId = byteArrayOf(0x08, 0x01, 0x02, 0x03, 0x04, 0x80.toByte())
        val zeroSizeByte = byteArrayOf(0xEC.toByte(), 0x00, 0x00)
        val pastParent = EbmlBuilder.master(EbmlId.INFO, EbmlBuilder.text(EbmlId.TITLE, "Clip")).copyOf(8)
        val hugeSize = byteArrayOf(0xEC.toByte(), 0x01, 0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFE.toByte())
        val oneByteIntoHeader = byteArrayOf(0x1A)
        for (bytes in listOf(fiveByteId, zeroSizeByte, pastParent, hugeSize, oneByteIntoHeader, ByteArray(0))) {
            assertThrows(MalformedFileException::class.java) {
                val reader = reader(bytes)
                reader.children(reader.readElement(0, bytes.size.toLong(), 0))
            }
        }
    }

    @Test
    fun capsNestingDepth() {
        var nested = EbmlBuilder.element(EbmlId.VOID, ByteArray(0))
        repeat(20) { nested = EbmlBuilder.master(EbmlId.SIMPLE_TAG, nested) }
        val reader = reader(nested)

        assertThrows(MalformedFileException::class.java) {
            var level = listOf(reader.readElement(0, nested.size.toLong(), 0))
            while (level.isEmpty() == false) {
                level = level.flatMap { if (it.id == EbmlId.SIMPLE_TAG) reader.children(it) else emptyList() }
            }
        }
    }

    private fun reader(bytes: ByteArray): EbmlReader = EbmlReader(BinaryReader(ByteArrayChannel(bytes)))

    // endregion Methods
}
