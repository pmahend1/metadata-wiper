package com.prateekmahendrakar.metadatawiper.metadata.isobmff

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import java.nio.ByteOrder

/*
 * Splits a byte range of an ISO BMFF file (HEIF, AVIF, later MP4 and MOV) into the boxes that
 * fill it. Handles 32-bit sizes, 64-bit sizes (size 1) and boxes that run to the end of their
 * container (size 0). The boxes must fill the range exactly.
 */
class BoxReader(private val reader: BinaryReader) {

    // region State

    private companion object {
        const val HEADER_SIZE = 8L
        const val LARGE_SIZE_FIELD = 8L
        const val USER_TYPE_SIZE = 16L
        const val FULL_BOX_HEADER_SIZE = 4L
        const val TYPE_SIZE = 4
        const val TYPE_OFFSET = 4L
        const val PRINTABLE_START = 0x20
        const val PRINTABLE_END = 0x7E
        const val COPYRIGHT_SIGN = 0xA9
        const val LARGE_SIZE = 1L
        const val TO_END = 0L
        const val UUID = "uuid"

        // A real container holds a few hundred boxes at most; this stops a crafted file building millions.
        const val MAX_BOXES = 100_000
    }

    // endregion State

    // region Methods

    fun read(start: Long,
             end: Long): List<Box> {
        if (start < 0 || start > end || end > reader.size) {
            throw MalformedFileException("Box range $start–$end is outside the file (${reader.size} bytes)")
        }
        val boxes = mutableListOf<Box>()
        var position = start
        while (position < end) {
            if (boxes.size == MAX_BOXES) {
                throw MalformedFileException("More than $MAX_BOXES boxes at offset $start")
            }
            val box = readBox(position, end)
            boxes += box
            position = box.end
        }
        return boxes
    }

    /*
     * Like read(0, size), except that bytes after the last box which don't start with a box
     * header are returned as a trailer instead of failing: some phones and cameras append data
     * there (Samsung's SEFT, Insta360). A header with a plausible type but a size that doesn't
     * fit is still corrupt, since that's what a truncated `mdat` looks like.
     */
    fun readTopLevel(): TopLevelBoxes {
        val boxes = mutableListOf<Box>()
        var position = 0L
        while (position < reader.size) {
            if (boxes.size == MAX_BOXES) {
                throw MalformedFileException("More than $MAX_BOXES top-level boxes")
            }
            if (startsWithBoxHeader(position) == false) {
                return TopLevelBoxes(boxes, FileSpan(position, reader.size))
            }
            val box = readBox(position, reader.size)
            boxes += box
            position = box.end
        }
        return TopLevelBoxes(boxes, trailing = null)
    }

    fun readChildren(parent: Box): List<Box> = read(parent.dataStart, parent.end)

    // A full box starts with a version byte and three flag bytes before its children.
    fun readFullBoxChildren(parent: Box,
                            skip: Long = 0): List<Box> = read(fullBoxDataStart(parent) + skip, parent.end)

    fun fullBoxDataStart(box: Box): Long {
        if (box.end - box.dataStart < FULL_BOX_HEADER_SIZE) {
            throw MalformedFileException("'${box.type}' box at offset ${box.start} is too short")
        }
        return box.dataStart + FULL_BOX_HEADER_SIZE
    }

    // Box types are four printable characters; QuickTime also uses '©' (0xA9) as the first one.
    private fun startsWithBoxHeader(position: Long): Boolean {
        if (reader.size - position < HEADER_SIZE) {
            return false
        }
        reader.seek(position + TYPE_OFFSET)
        return reader.readBytes(TYPE_SIZE).all {
            val value = it.toInt() and 0xFF
            value in PRINTABLE_START..PRINTABLE_END || value == COPYRIGHT_SIGN
        }
    }

    private fun readBox(start: Long,
                        containerEnd: Long): Box {
        if (containerEnd - start < HEADER_SIZE) {
            throw MalformedFileException("Box header at offset $start runs past the end of its container")
        }
        reader.seek(start)
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        val declaredSize = reader.readU32()
        // ISO-8859-1 maps every byte to one char, so types like QuickTime's "©xyz" survive intact.
        val type = String(reader.readBytes(TYPE_SIZE), Charsets.ISO_8859_1)

        var headerEnd = start + HEADER_SIZE
        val size = when (declaredSize) {
            LARGE_SIZE -> {
                if (containerEnd - headerEnd < LARGE_SIZE_FIELD) {
                    throw MalformedFileException("'$type' box at offset $start is cut off in its 64-bit size")
                }
                headerEnd += LARGE_SIZE_FIELD
                reader.readU64()
            }
            TO_END -> containerEnd - start
            else -> declaredSize
        }
        val dataStart = if (type == UUID) headerEnd + USER_TYPE_SIZE else headerEnd

        if (size < dataStart - start || size > containerEnd - start) {
            throw MalformedFileException("'$type' box at offset $start declares $size bytes, which doesn't fit its container")
        }
        return Box(type, start, headerEnd, dataStart, start + size)
    }

    // endregion Methods
}
