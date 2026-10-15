package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u16
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u64
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ItemLocationReaderTest {

    @Test
    fun readsVersion0() {
        // offset 4, length 4, base offset 0; one item with two extents.
        val iloc = fullBox("iloc", 0, 0, u8(0x44), u8(0x00), u16(1),
                           u16(7), u16(0), u16(2), u32(100), u32(10), u32(200), u32(0))
        assertEquals(listOf(ItemLocation(7, 0, 0, 0, listOf(ItemExtent(100, 10), ItemExtent(200, 0)))), read(iloc))
    }

    @Test
    fun readsVersion1WithIndexAndConstructionMethod() {
        // offset 4, length 4, base offset 4, index 4.
        val iloc = fullBox("iloc", 1, 0, u8(0x44), u8(0x44), u16(1),
                           u16(3), u16(1), u16(0), u32(50), u16(1), u32(9), u32(4), u32(6))
        assertEquals(listOf(ItemLocation(3, 1, 0, 50, listOf(ItemExtent(4, 6)))), read(iloc))
    }

    @Test
    fun readsVersion2WithWideFields() {
        // offset 8, length 8, base offset 8; 32-bit item IDs and count.
        val iloc = fullBox("iloc", 2, 0, u8(0x88), u8(0x80), u32(1),
                           u32(70_000), u16(0), u16(2), u64(5_000_000_000), u16(1), u64(16), u64(32))
        assertEquals(listOf(ItemLocation(70_000, 0, 2, 5_000_000_000, listOf(ItemExtent(16, 32)))), read(iloc))
    }

    @Test
    fun rejectsInvalidBoxes() {
        val cases = mapOf("version 3" to fullBox("iloc", 3, 0, u8(0x44), u8(0x00), u16(0)),
                          "field size 2" to fullBox("iloc", 0, 0, u8(0x24), u8(0x00), u16(0)),
                          "item count past end" to fullBox("iloc", 0, 0, u8(0x44), u8(0x00), u16(2),
                                                           u16(1), u16(0), u16(0)) + ByteArray(32))
        for ((name, iloc) in cases) {
            assertThrows(name, MalformedFileException::class.java) { read(iloc) }
        }
    }

    private fun read(bytes: ByteArray): List<ItemLocation> {
        val reader = BinaryReader(ByteArrayChannel(bytes))
        val iloc = BoxReader(reader).read(0, bytes.size.toLong()).first()
        return ItemLocationReader(reader).read(iloc)
    }
}
