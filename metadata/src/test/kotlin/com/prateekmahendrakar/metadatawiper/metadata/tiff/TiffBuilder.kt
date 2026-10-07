package com.prateekmahendrakar.metadatawiper.metadata.tiff

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Builds small TIFF blocks for tests, laid out as header, IFD0, EXIF IFD, GPS IFD, IFD1,
 * then out-of-line values. Sub-IFD pointers are added to IFD0 automatically.
 */
class TiffBuilder(private val order: ByteOrder) {

    // region Methods

    fun ascii(tag: Int,
              value: String): TiffTestEntry {
        val bytes = (value + "\u0000").toByteArray(Charsets.US_ASCII)
        return TiffTestEntry(tag, TiffTag.TYPE_ASCII, bytes.size.toLong(), bytes)
    }

    fun short(tag: Int,
              value: Int): TiffTestEntry {
        return TiffTestEntry(tag, TiffTag.TYPE_SHORT, 1, buffer(2).putShort(value.toShort()).array())
    }

    fun long(tag: Int,
             value: Long): TiffTestEntry {
        return TiffTestEntry(tag, TiffTag.TYPE_LONG, 1, buffer(4).putInt(value.toInt()).array())
    }

    fun rationals(tag: Int,
                  vararg values: Pair<Long, Long>): TiffTestEntry {
        val buffer = buffer(values.size * 8)
        values.forEach { (numerator, denominator) -> buffer.putInt(numerator.toInt()).putInt(denominator.toInt()) }
        return TiffTestEntry(tag, TiffTag.TYPE_RATIONAL, values.size.toLong(), buffer.array())
    }

    fun undefined(tag: Int,
                  size: Int): TiffTestEntry = TiffTestEntry(tag, 7, size.toLong(), ByteArray(size))

    fun build(ifd0: List<TiffTestEntry>,
              exif: List<TiffTestEntry> = emptyList(),
              gps: List<TiffTestEntry> = emptyList(),
              ifd1: List<TiffTestEntry> = emptyList()): ByteArray {
        val pointerCount = (if (exif.isEmpty()) 0 else 1) + (if (gps.isEmpty()) 0 else 1)
        val ifd0Offset = 8
        val exifOffset = ifd0Offset + ifdSize(ifd0.size + pointerCount)
        val gpsOffset = exifOffset + ifdSize(exif.size)
        val ifd1Offset = gpsOffset + ifdSize(gps.size)
        val dataOffset = ifd1Offset + ifdSize(ifd1.size)

        val pointers = mutableListOf<TiffTestEntry>()
        if (exif.isNotEmpty()) {
            pointers += long(TiffTag.EXIF_IFD_POINTER, exifOffset.toLong())
        }
        if (gps.isNotEmpty()) {
            pointers += long(TiffTag.GPS_IFD_POINTER, gpsOffset.toLong())
        }
        val allIfd0 = ifd0 + pointers

        val dataSize = (allIfd0 + exif + gps + ifd1).filter { it.bytes.size > 4 }.sumOf { it.bytes.size + it.bytes.size % 2 }
        val output = buffer(dataOffset + dataSize)
        output.put(if (order == ByteOrder.LITTLE_ENDIAN) TiffTag.LITTLE_ENDIAN_MARK else TiffTag.BIG_ENDIAN_MARK)
              .putShort(TiffTag.MAGIC.toShort())
              .putInt(ifd0Offset)

        var dataCursor = dataOffset
        val nextAfterIfd0 = if (ifd1.isEmpty()) 0 else ifd1Offset
        dataCursor = writeIfd(output, ifd0Offset, allIfd0, nextAfterIfd0, dataCursor)
        dataCursor = writeIfd(output, exifOffset, exif, 0, dataCursor)
        dataCursor = writeIfd(output, gpsOffset, gps, 0, dataCursor)
        writeIfd(output, ifd1Offset, ifd1, 0, dataCursor)
        return output.array()
    }

    private fun writeIfd(output: ByteBuffer,
                         offset: Int,
                         entries: List<TiffTestEntry>,
                         nextIfdOffset: Int,
                         dataCursor: Int): Int {
        if (entries.isEmpty()) {
            return dataCursor
        }
        var cursor = dataCursor
        output.position(offset)
        output.putShort(entries.size.toShort())
        for (entry in entries.sortedBy { it.tag }) {
            output.putShort(entry.tag.toShort())
                  .putShort(entry.type.toShort())
                  .putInt(entry.count.toInt())
            if (entry.bytes.size <= 4) {
                output.put(entry.bytes + ByteArray(4 - entry.bytes.size))
            } else {
                output.putInt(cursor)
                output.put(cursor, entry.bytes)
                cursor += entry.bytes.size + entry.bytes.size % 2
            }
        }
        output.putInt(nextIfdOffset)
        return cursor
    }

    private fun ifdSize(entryCount: Int): Int = if (entryCount == 0) 0 else 2 + entryCount * 12 + 4

    private fun buffer(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(order)

    // endregion Methods
}
