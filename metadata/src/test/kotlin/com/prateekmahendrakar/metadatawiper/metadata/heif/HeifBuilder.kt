package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.ascii
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.cString
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.largeBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.sized
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u16
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u8

/*
 * Builds small synthetic HEIF files for the cases encoders don't produce on request: `idat`
 * storage, split extents, `iloc` version 2, item names, extra boxes. Item data is laid out in
 * `mdat` (or `idat`) in item order; nothing is real HEVC, which the handler never decodes.
 */
class HeifBuilder(private val items: List<HeifTestItem>) {

    // region State

    var ilocVersion = 1
    var fieldSize = 4
    var largeMediaData = false
    var properties = listOf<ByteArray>()

    // (item ID, 1-based property index, essential)
    var associations = listOf<Triple<Int, Int, Boolean>>()
    var references = listOf<ByteArray>()
    var extraMetaBoxes = listOf<ByteArray>()
    var trailingBoxes = listOf<ByteArray>()

    // endregion State

    // region Methods

    fun build(): ByteArray {
        val ftyp = box("ftyp", ascii("heic"), u32(0), ascii("mif1"), ascii("heic"))
        // The meta box's size doesn't depend on the offsets it holds, so a first pass finds where mdat's data starts.
        val mediaHeaderSize = if (largeMediaData) 16 else 8
        val mediaDataStart = ftyp.size + meta(0).size + mediaHeaderSize.toLong()
        val mediaPayload = concat(*items.filter { it.storedInMediaData() }.map { it.data }.toTypedArray())
        val mdat = if (largeMediaData) largeBox("mdat", mediaPayload) else box("mdat", mediaPayload)
        return concat(ftyp, meta(mediaDataStart), mdat, *trailingBoxes.toTypedArray())
    }

    private fun meta(mediaDataStart: Long): ByteArray {
        val idatPayload = concat(*items.filter { it.constructionMethod == 1 }.map { it.data }.toTypedArray())
        val children = mutableListOf(fullBox("hdlr", 0, 0, u32(0), ascii("pict"), ByteArray(12), cString("")),
                                     fullBox("pitm", 0, 0, u16(items.first().id)),
                                     iloc(mediaDataStart),
                                     iinf())
        if (properties.isNotEmpty()) {
            children += box("iprp", box("ipco", *properties.toTypedArray()), ipma())
        }
        if (references.isNotEmpty()) {
            children += fullBox("iref", 0, 0, *references.toTypedArray())
        }
        if (idatPayload.isNotEmpty()) {
            children += box("idat", idatPayload)
        }
        children += extraMetaBoxes
        return fullBox("meta", 0, 0, *children.toTypedArray())
    }

    private fun iloc(mediaDataStart: Long): ByteArray {
        val parts = mutableListOf(u8((fieldSize shl 4) or fieldSize), u8(fieldSize shl 4))
        parts += if (ilocVersion < 2) u16(items.size) else u32(items.size.toLong())
        var nextMediaOffset = mediaDataStart
        var idatOffset = 0L
        for (item in items) {
            val base = when {
                item.mediaOffset != null -> mediaDataStart + item.mediaOffset
                item.storedInMediaData() -> nextMediaOffset.also { nextMediaOffset += item.data.size }
                item.constructionMethod == 1 -> idatOffset.also { idatOffset += item.data.size }
                else -> 0L
            }
            parts += if (ilocVersion < 2) u16(item.id) else u32(item.id.toLong())
            if (ilocVersion > 0) {
                parts += u16(item.constructionMethod)
            }
            parts += u16(item.dataReferenceIndex)
            parts += sized(base, fieldSize)
            parts += u16(item.extents)
            val extentSize = item.data.size / item.extents
            for (extent in 0 until item.extents) {
                val offset = extent * extentSize.toLong()
                val length = if (extent == item.extents - 1) item.data.size - offset else extentSize.toLong()
                parts += sized(offset, fieldSize) + sized(length, fieldSize)
            }
        }
        return fullBox("iloc", ilocVersion, 0, *parts.toTypedArray())
    }

    private fun iinf(): ByteArray {
        val entries = items.map { item ->
            val mimeFields = if (item.type == "mime") cString(item.contentType) + cString("") else ByteArray(0)
            fullBox("infe", 2, 0, u16(item.id), u16(0), ascii(item.type), cString(item.name), mimeFields)
        }
        return fullBox("iinf", 0, 0, u16(entries.size), *entries.toTypedArray())
    }

    private fun ipma(): ByteArray {
        val byItem = associations.groupBy { it.first }
        val entries = byItem.map { (itemId, list) ->
            concat(u16(itemId),
                   u8(list.size),
                   *list.map { (_, index, essential) -> u8((if (essential) 0x80 else 0) or index) }.toTypedArray())
        }
        return fullBox("ipma", 0, 0, u32(entries.size.toLong()), *entries.toTypedArray())
    }

    private fun HeifTestItem.storedInMediaData(): Boolean = constructionMethod == 0 && mediaOffset == null

    // endregion Methods
}
