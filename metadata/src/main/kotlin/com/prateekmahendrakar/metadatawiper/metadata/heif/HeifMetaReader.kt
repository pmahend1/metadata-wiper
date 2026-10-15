package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.ItemLocationReader
import java.nio.ByteOrder

// Parses a HEIF `meta` box: item info, locations, references and properties.
class HeifMetaReader(private val reader: BinaryReader,
                     private val boxReader: BoxReader) {

    // region State

    private companion object {
        const val HDLR = "hdlr"
        const val IINF = "iinf"
        const val INFE = "infe"
        const val ILOC = "iloc"
        const val IREF = "iref"
        const val IPRP = "iprp"
        const val IPCO = "ipco"
        const val IPMA = "ipma"
        const val IDAT = "idat"
        const val COLR = "colr"
        const val AUXC = "auxC"
        const val MIME = "mime"
        const val PICTURE_HANDLER = "pict"

        const val TYPE_SIZE = 4
        const val HANDLER_TYPE_OFFSET = 4L
        const val LARGE_PROPERTY_INDEX_FLAG = 1

        // Item info and `auxC` boxes hold a few short strings, so anything bigger is corrupt.
        const val MAX_STRING_BOX_SIZE = 64 * 1024L
    }

    // endregion State

    // region Methods

    fun read(meta: Box): HeifMeta {
        val children = boxReader.readFullBoxChildren(meta)
        val handler = atMostOne(children, HDLR) ?: throw MalformedFileException("HEIF 'meta' box has no handler")
        if (readHandlerType(handler) != PICTURE_HANDLER) {
            throw MalformedFileException("HEIF 'meta' box isn't for a picture")
        }

        val locations = atMostOne(children, ILOC)?.let { ItemLocationReader(reader).read(it) } ?: emptyList()
        val propertyBoxes = atMostOne(children, IPRP)?.let { boxReader.readChildren(it) } ?: emptyList()
        return HeifMeta(children = children,
                        items = atMostOne(children, IINF)?.let { readItems(it) } ?: emptyList(),
                        locations = locations.associateBy { it.itemId },
                        references = atMostOne(children, IREF)?.let { readReferences(it) } ?: emptyList(),
                        properties = atMostOne(propertyBoxes, IPCO)?.let { readProperties(it) } ?: emptyList(),
                        associations = propertyBoxes.filter { it.type == IPMA }.flatMap { readAssociations(it) },
                        idat = atMostOne(children, IDAT))
    }

    private fun atMostOne(boxes: List<Box>,
                          type: String): Box? {
        val found = boxes.filter { it.type == type }
        if (found.size > 1) {
            throw MalformedFileException("HEIF file has ${found.size} '$type' boxes where one is allowed")
        }
        return found.firstOrNull()
    }

    private fun readHandlerType(hdlr: Box): String {
        val start = boxReader.fullBoxDataStart(hdlr) + HANDLER_TYPE_OFFSET
        if (hdlr.end - start < TYPE_SIZE) {
            throw MalformedFileException("HEIF handler box is too short")
        }
        reader.seek(start)
        return readType()
    }

    private fun readItems(iinf: Box): List<HeifItem> {
        val version = readVersion(iinf)
        val countSize = if (version == 0) 2L else 4L
        return boxReader.readFullBoxChildren(iinf, skip = countSize)
                        .filter { it.type == INFE }
                        .map { readItem(it) }
    }

    /*
     * Version 2 and 3 entries, which HEIF requires: version 2 has 16-bit item IDs, version 3
     * 32-bit ones. The strings are null-terminated, though a writer may leave the last
     * terminator off, so a string also ends at the end of the box.
     */
    private fun readItem(infe: Box): HeifItem {
        val version = readVersion(infe)
        if (version < 2) {
            throw MalformedFileException("HEIF item info version $version isn't supported")
        }
        val id = if (version == 2) reader.readU16().toLong() else reader.readU32()
        reader.readU16() // item_protection_index
        val type = readType()

        val name = readString(infe)
        var contentType = ""
        var contentEncoding = ""
        if (type == MIME) {
            contentType = text(readString(infe))
            contentEncoding = text(readString(infe))
        }
        return HeifItem(id, type, name, contentType, contentEncoding)
    }

    private fun readReferences(iref: Box): List<HeifReference> {
        val version = readVersion(iref)
        return boxReader.readFullBoxChildren(iref).map { reference ->
            reader.seek(reference.dataStart)
            val fromId = readItemId(version)
            val count = reader.readU16()
            val toIds = List(count) { readItemId(version) }
            requireInside(reference)
            HeifReference(reference.type, fromId, toIds)
        }
    }

    private fun readProperties(ipco: Box): List<HeifProperty> {
        return boxReader.readChildren(ipco).mapIndexed { position, box ->
            val subtype = when (box.type) {
                COLR -> readColourType(box)
                AUXC -> readAuxiliaryType(box)
                else -> ""
            }
            HeifProperty(position + 1, box, subtype)
        }
    }

    private fun readColourType(colr: Box): String {
        if (colr.end - colr.dataStart < TYPE_SIZE) {
            throw MalformedFileException("HEIF colour property at offset ${colr.start} is too short")
        }
        reader.seek(colr.dataStart)
        return readType()
    }

    private fun readAuxiliaryType(auxC: Box): String {
        reader.seek(boxReader.fullBoxDataStart(auxC))
        return text(readString(auxC))
    }

    private fun readAssociations(ipma: Box): List<HeifPropertyAssociation> {
        val version = readVersion(ipma)
        reader.seek(ipma.dataStart + 3)
        val flags = reader.readU8()
        val entryCount = reader.readU32()

        val associations = mutableListOf<HeifPropertyAssociation>()
        for (entry in 0 until entryCount) {
            requireInside(ipma)
            val itemId = readItemId(version)
            repeat(reader.readU8()) {
                val flagOffset = reader.position
                val flagByte: Int
                val index: Int
                if (flags and LARGE_PROPERTY_INDEX_FLAG == LARGE_PROPERTY_INDEX_FLAG) {
                    val value = reader.readU16()
                    flagByte = value shr 8
                    index = value and 0x7FFF
                } else {
                    flagByte = reader.readU8()
                    index = flagByte and 0x7F
                }
                associations += HeifPropertyAssociation(itemId, index, flagByte and 0x80 == 0x80, flagOffset, flagByte)
            }
        }
        requireInside(ipma)
        return associations
    }

    // Reads a full box's version and skips its flags, leaving the reader at the box's own fields.
    private fun readVersion(box: Box): Int {
        val fieldsStart = boxReader.fullBoxDataStart(box)
        reader.seek(box.dataStart)
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        val version = reader.readU8()
        reader.seek(fieldsStart)
        return version
    }

    private fun readItemId(version: Int): Long = if (version == 0) reader.readU16().toLong() else reader.readU32()

    private fun readType(): String = String(reader.readBytes(TYPE_SIZE), Charsets.ISO_8859_1)

    // The span of a null-terminated string from the reader's position, leaving the reader after its terminator.
    private fun readString(box: Box): FileSpan {
        if (box.end - box.start > MAX_STRING_BOX_SIZE) {
            throw MalformedFileException("'${box.type}' box at offset ${box.start} is too large")
        }
        val start = reader.position
        if (start > box.end) {
            throw MalformedFileException("'${box.type}' box at offset ${box.start} runs past its end")
        }
        val bytes = reader.readBytes((box.end - start).toInt())
        val length = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        reader.seek(minOf(start + length + 1, box.end))
        return FileSpan(start, start + length)
    }

    // Leaves the reader where it was, so the next string can follow.
    private fun text(span: FileSpan): String {
        val position = reader.position
        reader.seek(span.start)
        val text = String(reader.readBytes(span.length.toInt()), Charsets.UTF_8)
        reader.seek(position)
        return text
    }

    private fun requireInside(box: Box) {
        if (reader.position > box.end) {
            throw MalformedFileException("'${box.type}' box at offset ${box.start} runs past its end")
        }
    }

    // endregion Methods
}
