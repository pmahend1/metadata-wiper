package com.prateekmahendrakar.metadatawiper.metadata.tiff

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.nio.ByteOrder
import java.util.Locale

/*
 * Reads just enough of an EXIF/TIFF block to tell the user what identifying data it holds:
 * IFD0, the EXIF and GPS sub-IFDs it points to, and IFD1 for the thumbnail. It doesn't need
 * to understand every tag, because stripping removes the whole block regardless.
 *
 * The TIFF data lives inside the file at [tiffStart]; every offset in it is relative to that
 * and must stay within [tiffLength]. Anything outside throws MalformedFileException.
 */
class TiffSummaryReader(private val reader: BinaryReader,
                        private val tiffStart: Long,
                        private val tiffLength: Long) {

    // region State

    private val textTags = mapOf(TiffTag.IMAGE_DESCRIPTION to TextTag("ImageDescription", MetadataCategory.Other),
                                 TiffTag.MAKE to TextTag("Make", MetadataCategory.Device),
                                 TiffTag.MODEL to TextTag("Model", MetadataCategory.Device),
                                 TiffTag.SOFTWARE to TextTag("Software", MetadataCategory.Software),
                                 TiffTag.DATE_TIME to TextTag("DateTime", MetadataCategory.DateTime),
                                 TiffTag.ARTIST to TextTag("Artist", MetadataCategory.People),
                                 TiffTag.HOST_COMPUTER to TextTag("HostComputer", MetadataCategory.Device),
                                 TiffTag.COPYRIGHT to TextTag("Copyright", MetadataCategory.People),
                                 TiffTag.DATE_TIME_ORIGINAL to TextTag("DateTimeOriginal", MetadataCategory.DateTime),
                                 TiffTag.DATE_TIME_DIGITIZED to TextTag("DateTimeDigitized", MetadataCategory.DateTime),
                                 TiffTag.IMAGE_UNIQUE_ID to TextTag("ImageUniqueID", MetadataCategory.Device),
                                 TiffTag.CAMERA_OWNER_NAME to TextTag("CameraOwnerName", MetadataCategory.People),
                                 TiffTag.BODY_SERIAL_NUMBER to TextTag("BodySerialNumber", MetadataCategory.Device),
                                 TiffTag.LENS_MAKE to TextTag("LensMake", MetadataCategory.Device),
                                 TiffTag.LENS_MODEL to TextTag("LensModel", MetadataCategory.Device),
                                 TiffTag.LENS_SERIAL_NUMBER to TextTag("LensSerialNumber", MetadataCategory.Device))

    private companion object {
        const val HEADER_SIZE = 8L
        const val ENTRY_SIZE = 12L
        const val INLINE_VALUE_SIZE = 4L
        const val MAX_TEXT_LENGTH = 256
    }

    // endregion State

    // region Methods

    fun read(): TiffSummary {
        val originalOrder = reader.byteOrder
        try {
            return readSummary()
        } finally {
            reader.byteOrder = originalOrder
        }
    }

    private fun readSummary(): TiffSummary {
        if (tiffLength < HEADER_SIZE) {
            throw MalformedFileException("TIFF block is too short ($tiffLength bytes)")
        }
        reader.seek(tiffStart)
        reader.byteOrder = readByteOrder()
        if (reader.readU16() != TiffTag.MAGIC) {
            throw MalformedFileException("TIFF header has the wrong magic number")
        }

        val ifd0Offset = reader.readU32()
        val ifd0 = readIfd(ifd0Offset)
        val exifIfd = readSubIfd(ifd0, TiffTag.EXIF_IFD_POINTER)
        val gpsIfd = readSubIfd(ifd0, TiffTag.GPS_IFD_POINTER)

        val items = mutableListOf<MetadataItem>()
        items += textItems(ifd0)
        if (exifIfd != null) {
            items += textItems(exifIfd)
            items += opaqueItem(exifIfd, TiffTag.MAKER_NOTE, "MakerNote", MetadataCategory.Device)
            items += opaqueItem(exifIfd, TiffTag.USER_COMMENT, "UserComment", MetadataCategory.Other)
        }
        if (gpsIfd != null && gpsIfd.entries.isNotEmpty()) {
            items += MetadataItem(MetadataCategory.Location, "GPS", gpsCoordinates(gpsIfd) ?: "")
        }

        val orientation = ifd0.entries[TiffTag.ORIENTATION]?.let { readUnsigned(it) }?.toInt()
        if (orientation != null) {
            items += MetadataItem(MetadataCategory.Orientation, "Orientation", orientation.toString())
        }

        // IFD1 holds the thumbnail. An offset pointing back at IFD0 would loop, so it's ignored.
        if (ifd0.nextIfdOffset != 0L && ifd0.nextIfdOffset != ifd0Offset) {
            val ifd1 = readIfd(ifd0.nextIfdOffset)
            if (ifd1.entries.isNotEmpty()) {
                val thumbnailLength = ifd1.entries[TiffTag.THUMBNAIL_LENGTH]?.let { readUnsigned(it) } ?: 0L
                items += MetadataItem(MetadataCategory.Thumbnail, "Thumbnail", byteSize = thumbnailLength)
            }
        }

        return TiffSummary(orientation, items)
    }

    private fun readByteOrder(): ByteOrder {
        val mark = reader.readBytes(2)
        if (mark.contentEquals(TiffTag.LITTLE_ENDIAN_MARK)) {
            return ByteOrder.LITTLE_ENDIAN
        }
        if (mark.contentEquals(TiffTag.BIG_ENDIAN_MARK)) {
            return ByteOrder.BIG_ENDIAN
        }
        throw MalformedFileException("TIFF header has an unknown byte order mark")
    }

    private fun readSubIfd(parent: TiffIfd,
                           pointerTag: Int): TiffIfd? {
        val pointer = parent.entries[pointerTag] ?: return null
        return readIfd(readUnsigned(pointer))
    }

    private fun readIfd(offset: Long): TiffIfd {
        requireInside(offset, 2)
        reader.seek(tiffStart + offset)
        val entryCount = reader.readU16()
        val entriesSize = entryCount * ENTRY_SIZE
        requireInside(offset + 2, entriesSize + INLINE_VALUE_SIZE)

        val entries = mutableMapOf<Int, TiffEntry>()
        for (index in 0 until entryCount) {
            val entryOffset = offset + 2 + index * ENTRY_SIZE
            reader.seek(tiffStart + entryOffset)
            val entry = readEntry(entryOffset)
            if (entry != null) {
                entries[entry.tag] = entry
            }
        }

        reader.seek(tiffStart + offset + 2 + entriesSize)
        return TiffIfd(entries, reader.readU32())
    }

    // The TIFF spec says readers must skip entries with a type they don't know.
    private fun readEntry(entryOffset: Long): TiffEntry? {
        val tag = reader.readU16()
        val type = reader.readU16()
        val count = reader.readU32()
        val typeSize = typeSize(type) ?: return null
        val valueSize = count * typeSize
        val valueOffset = if (valueSize <= INLINE_VALUE_SIZE) entryOffset + 8 else reader.readU32()
        requireInside(valueOffset, valueSize)
        return TiffEntry(tag, type, count, valueOffset, valueSize)
    }

    private fun textItems(ifd: TiffIfd): List<MetadataItem> {
        return ifd.entries.values.mapNotNull { entry ->
            val textTag = textTags[entry.tag] ?: return@mapNotNull null
            val text = readText(entry)
            if (text.isEmpty()) {
                return@mapNotNull null
            }
            MetadataItem(textTag.category, textTag.label, text, entry.valueSize)
        }
    }

    private fun opaqueItem(ifd: TiffIfd,
                           tag: Int,
                           label: String,
                           category: MetadataCategory): List<MetadataItem> {
        val entry = ifd.entries[tag] ?: return emptyList()
        if (entry.valueSize == 0L) {
            return emptyList()
        }
        return listOf(MetadataItem(category, label, byteSize = entry.valueSize))
    }

    // Formatted as "lat, lon" in decimal degrees, or null when either coordinate is missing.
    private fun gpsCoordinates(gpsIfd: TiffIfd): String? {
        val latitude = coordinate(gpsIfd, TiffTag.GPS_LATITUDE, TiffTag.GPS_LATITUDE_REF, "S") ?: return null
        val longitude = coordinate(gpsIfd, TiffTag.GPS_LONGITUDE, TiffTag.GPS_LONGITUDE_REF, "W") ?: return null
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
    }

    private fun coordinate(gpsIfd: TiffIfd,
                           valueTag: Int,
                           refTag: Int,
                           negativeRef: String): Double? {
        val value = gpsIfd.entries[valueTag] ?: return null
        if (value.type != TiffTag.TYPE_RATIONAL || value.count != 3L) {
            return null
        }
        val (degrees, minutes, seconds) = readRationals(value)
        val decimal = degrees + minutes / 60 + seconds / 3600
        if (decimal.isFinite() == false) {
            return null
        }
        val ref = gpsIfd.entries[refTag]?.let { readText(it) }
        return if (ref == negativeRef) -decimal else decimal
    }

    private fun readText(entry: TiffEntry): String {
        reader.seek(tiffStart + entry.valueOffset)
        val bytes = reader.readBytes(minOf(entry.valueSize, MAX_TEXT_LENGTH.toLong()).toInt())
        val terminator = bytes.indexOf(0).let { if (it == -1) bytes.size else it }
        return String(bytes, 0, terminator, Charsets.UTF_8).trim()
    }

    private fun readUnsigned(entry: TiffEntry): Long {
        reader.seek(tiffStart + entry.valueOffset)
        return when (entry.type) {
            TiffTag.TYPE_BYTE -> reader.readU8().toLong()
            TiffTag.TYPE_SHORT -> reader.readU16().toLong()
            TiffTag.TYPE_LONG, TiffTag.TYPE_IFD -> reader.readU32()
            else -> throw MalformedFileException("Tag ${entry.tag} has non-integer type ${entry.type}")
        }
    }

    private fun readRationals(entry: TiffEntry): List<Double> {
        reader.seek(tiffStart + entry.valueOffset)
        return List(entry.count.toInt()) {
            val numerator = reader.readU32()
            val denominator = reader.readU32()
            numerator.toDouble() / denominator
        }
    }

    private fun requireInside(offset: Long,
                              size: Long) {
        if (offset < 0 || size < 0 || offset + size > tiffLength) {
            throw MalformedFileException("TIFF data at $offset (+$size bytes) is outside the $tiffLength-byte block")
        }
    }

    private fun typeSize(type: Int): Long? {
        return when (type) {
            1, 2, 6, 7 -> 1L
            3, 8 -> 2L
            4, 9, 11, 13 -> 4L
            5, 10, 12 -> 8L
            else -> null
        }
    }

    // endregion Methods
}
