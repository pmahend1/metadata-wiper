package com.prateekmahendrakar.metadatawiper.metadata.ebml

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader

/*
 * Reads EBML elements, the structure of Matroska and WebM. IDs are up to 4 bytes and sizes up
 * to 8, and every element has to fit inside its parent. Only Segment and Cluster may have an
 * unknown size, as a live recorder writes them: a Segment then runs to the end of its container
 * and a Cluster to the next element that can only be a sibling.
 */
class EbmlReader(private val reader: BinaryReader) {

    // region State

    private companion object {
        const val MAX_ID_LENGTH = 4
        const val MAX_SIZE_LENGTH = 8
        const val MAX_DEPTH = 16
        const val MAX_UNSIGNED_LENGTH = 8L
        const val MAX_TEXT_SIZE = 4096L
    }

    // endregion State

    // region Methods

    fun readElement(position: Long,
                    containerEnd: Long,
                    depth: Int): EbmlElement {
        if (depth > MAX_DEPTH) {
            throw MalformedFileException("Elements nest deeper than $MAX_DEPTH levels at offset $position")
        }
        val header = readHeader(position, containerEnd)
        val end = when {
            header.size != null -> header.dataStart + header.size
            header.id == EbmlId.SEGMENT -> containerEnd
            header.id == EbmlId.CLUSTER -> clusterEnd(header.dataStart, containerEnd)
            else -> throw MalformedFileException("Element ${hex(header.id)} at offset $position has an unknown size")
        }
        if (end > containerEnd) {
            throw MalformedFileException("Element ${hex(header.id)} at offset $position runs past its parent")
        }
        return EbmlElement(header.id, position, header.dataStart, end, depth)
    }

    fun children(parent: EbmlElement): List<EbmlElement> {
        val children = mutableListOf<EbmlElement>()
        var position = parent.dataStart
        while (position < parent.end) {
            val child = readElement(position, parent.end, parent.depth + 1)
            children += child
            position = child.end
        }
        return children
    }

    fun readUnsigned(element: EbmlElement): Long {
        if (element.dataLength > MAX_UNSIGNED_LENGTH) {
            throw MalformedFileException("Integer at offset ${element.start} is ${element.dataLength} bytes long")
        }
        reader.seek(element.dataStart)
        var value = 0L
        repeat(element.dataLength.toInt()) { value = (value shl 8) or reader.readU8().toLong() }
        return value
    }

    // EBML strings may be padded with zeros. A value is shown in one row, so a crafted megabyte isn't read.
    fun readText(element: EbmlElement): String {
        reader.seek(element.dataStart)
        val bytes = reader.readBytes(minOf(element.dataLength, MAX_TEXT_SIZE).toInt())
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
    }

    fun readBytes(element: EbmlElement): ByteArray {
        reader.seek(element.dataStart)
        return reader.readBytes(element.dataLength.toInt())
    }

    fun readHeader(position: Long,
                   containerEnd: Long): EbmlHeader {
        if (containerEnd > reader.size) {
            throw MalformedFileException("Element at offset $position runs past the end of the file")
        }
        reader.seek(position)
        val id = readId(containerEnd)
        val size = readSize(containerEnd)
        return EbmlHeader(id, position, reader.position, size)
    }

    // A level-1 ID can't appear inside a Cluster, so the first one marks where the Cluster ends.
    private fun clusterEnd(dataStart: Long,
                           containerEnd: Long): Long {
        var position = dataStart
        while (position < containerEnd) {
            val header = readHeader(position, containerEnd)
            if (header.id in EbmlId.segmentChildren || header.id == EbmlId.EBML) {
                return position
            }
            if (header.size == null) {
                throw MalformedFileException("Element ${hex(header.id)} at offset $position has an unknown size")
            }
            position = header.dataStart + header.size
        }
        return containerEnd
    }

    private fun readId(containerEnd: Long): Long {
        val first = readVintByte(containerEnd)
        val length = vintLength(first)
        if (length > MAX_ID_LENGTH) {
            throw MalformedFileException("Element ID at offset ${reader.position - 1} is longer than $MAX_ID_LENGTH bytes")
        }
        var id = first.toLong()
        repeat(length - 1) { id = (id shl 8) or readVintByte(containerEnd).toLong() }
        return id
    }

    // A size with every value bit set means "unknown"; it's returned as null.
    private fun readSize(containerEnd: Long): Long? {
        val first = readVintByte(containerEnd)
        val length = vintLength(first)
        if (length > MAX_SIZE_LENGTH) {
            throw MalformedFileException("Element size at offset ${reader.position - 1} is longer than $MAX_SIZE_LENGTH bytes")
        }
        var value = (first and (0xFF shr length)).toLong()
        repeat(length - 1) { value = (value shl 8) or readVintByte(containerEnd).toLong() }
        val unknown = (1L shl (7 * length)) - 1
        return if (value == unknown) null else value
    }

    private fun readVintByte(containerEnd: Long): Int {
        if (reader.position >= containerEnd) {
            throw MalformedFileException("Element header at offset ${reader.position} runs past its parent")
        }
        return reader.readU8()
    }

    // The count of leading zero bits in the first byte, plus one; a zero byte would mean more than 8.
    private fun vintLength(first: Int): Int {
        if (first == 0) {
            return MAX_SIZE_LENGTH + 1
        }
        return Integer.numberOfLeadingZeros(first) - 23
    }

    private fun hex(id: Long): String = "0x" + id.toString(16).uppercase()

    // endregion Methods
}
