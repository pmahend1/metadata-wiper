package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.ascii
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.largeBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoxReaderTest {

    @Test
    fun readsCompactLargeAndUuidBoxes() {
        val bytes = box("ftyp", ByteArray(8)) + largeBox("mdat", ByteArray(5)) + box("uuid", ByteArray(16), ByteArray(3))
        assertEquals(listOf(Box("ftyp", 0, 8, 8, 16),
                            Box("mdat", 16, 32, 32, 37),
                            Box("uuid", 37, 45, 61, 64)),
                     read(bytes))
    }

    @Test
    fun sizeZeroRunsToEndOfContainer() {
        val bytes = box("ftyp", ByteArray(4)) + u32(0) + ascii("mdat") + ByteArray(20)
        assertEquals(Box("mdat", 12, 20, 20, 40), read(bytes).last())
    }

    @Test
    fun readsChildrenOfFullBox() {
        val parent = fullBox("meta", 0, 0, box("hdlr", ByteArray(4)), box("free"))
        val reader = BoxReader(BinaryReader(ByteArrayChannel(parent)))
        val meta = reader.read(0, parent.size.toLong()).single()
        assertEquals(listOf("hdlr", "free"), reader.readFullBoxChildren(meta).map { it.type })
    }

    @Test
    fun rejectsBoxesThatDontFitTheirContainer() {
        val cases = mapOf("size past end" to u32(100) + ascii("mdat") + ByteArray(4),
                          "size below header" to u32(4) + ascii("mdat"),
                          "cut-off header" to box("ftyp") + ByteArray(5),
                          "cut-off large size" to u32(1) + ascii("mdat") + ByteArray(4),
                          "uuid without user type" to box("uuid", ByteArray(8)))
        for ((name, bytes) in cases) {
            assertThrows(name, MalformedFileException::class.java) { read(bytes) }
        }
    }

    @Test
    fun rejectsRangeOutsideFile() {
        val bytes = box("free")
        val reader = BoxReader(BinaryReader(ByteArrayChannel(bytes)))
        assertThrows(MalformedFileException::class.java) { reader.read(0, 100) }
    }

    private fun read(bytes: ByteArray): List<Box> = BoxReader(BinaryReader(ByteArrayChannel(bytes))).read(0, bytes.size.toLong())
}
