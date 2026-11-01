package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import java.nio.ByteBuffer
import java.util.Locale

/*
 * Lists what a `udta` or `meta` box holds. It only decides what the user is shown: the box is
 * freed whatever it contains, so a child that can't be parsed reports the whole box as unknown
 * instead of failing the file.
 */
class MovieMetadataReader(private val reader: BinaryReader,
                          private val boxReader: BoxReader) {

    // region State

    private val device = MetadataCategory.Device
    private val dates = MetadataCategory.DateTime
    private val people = MetadataCategory.People
    private val software = MetadataCategory.Software
    private val other = MetadataCategory.Other

    // QuickTime's international text: a length, a language, then the string.
    private val quickTimeTags = mapOf("©mak" to (device to MetadataLabel.MAKE),
                                      "©mod" to (device to MetadataLabel.MODEL),
                                      "©day" to (dates to MetadataLabel.DATE_TIME_ORIGINAL),
                                      "©swr" to (software to MetadataLabel.SOFTWARE),
                                      "©too" to (software to MetadataLabel.SOFTWARE),
                                      "©nam" to (other to MetadataLabel.TITLE),
                                      "©ART" to (people to MetadataLabel.ARTIST),
                                      "©aut" to (people to MetadataLabel.ARTIST),
                                      "©cmt" to (other to MetadataLabel.COMMENT),
                                      "©des" to (other to MetadataLabel.IMAGE_DESCRIPTION),
                                      "©cpy" to (people to MetadataLabel.COPYRIGHT))

    // 3GPP's strings: version and flags, a language, then the string.
    private val threeGppTags = mapOf("auth" to (people to MetadataLabel.ARTIST),
                                     "perf" to (people to MetadataLabel.ARTIST),
                                     "titl" to (other to MetadataLabel.TITLE),
                                     "dscp" to (other to MetadataLabel.IMAGE_DESCRIPTION),
                                     "cprt" to (people to MetadataLabel.COPYRIGHT))

    private val vendorBoxes = setOf("smta", "GPMF", "FIRM", "CAME", "LENS", "PANA")

    private val itemListTags = quickTimeTags + mapOf("aART" to (people to MetadataLabel.ARTIST),
                                                     "cprt" to (people to MetadataLabel.COPYRIGHT),
                                                     "desc" to (other to MetadataLabel.IMAGE_DESCRIPTION),
                                                     "ldes" to (other to MetadataLabel.IMAGE_DESCRIPTION),
                                                     "covr" to (MetadataCategory.Thumbnail to MetadataLabel.THUMBNAIL))

    private val keyTags = mapOf(LOCATION_KEY to (MetadataCategory.Location to MetadataLabel.GPS),
                                "com.apple.quicktime.make" to (device to MetadataLabel.MAKE),
                                "com.apple.quicktime.model" to (device to MetadataLabel.MODEL),
                                "com.apple.quicktime.software" to (software to MetadataLabel.SOFTWARE),
                                "com.apple.quicktime.creationdate" to (dates to MetadataLabel.DATE_TIME_ORIGINAL),
                                "com.apple.quicktime.content.identifier" to (other to MetadataLabel.IMAGE_UNIQUE_ID),
                                "com.apple.quicktime.author" to (people to MetadataLabel.ARTIST),
                                "com.apple.quicktime.title" to (other to MetadataLabel.TITLE),
                                "com.apple.quicktime.description" to (other to MetadataLabel.IMAGE_DESCRIPTION),
                                "com.apple.quicktime.comment" to (other to MetadataLabel.COMMENT),
                                "com.apple.quicktime.copyright" to (people to MetadataLabel.COPYRIGHT),
                                "com.android.manufacturer" to (device to MetadataLabel.MAKE),
                                "com.android.model" to (device to MetadataLabel.MODEL),
                                "com.android.version" to (software to MetadataLabel.SOFTWARE))

    // Degrees only. The minutes and seconds forms ISO 6709 also allows don't appear in video files.
    private val iso6709 = Regex("""^([+-]\d{2}(?:\.\d+)?)([+-]\d{3}(?:\.\d+)?)""")

    private val xmpUuid = byteArrayOf(0xBE.toByte(), 0x7A, 0xCF.toByte(), 0xCB.toByte(), 0x97.toByte(), 0xA9.toByte(),
                                      0x42, 0xE8.toByte(), 0x9C.toByte(), 0x71, 0x99.toByte(), 0x94.toByte(),
                                      0x91.toByte(), 0xE3.toByte(), 0xAF.toByte(), 0xAC.toByte())

    private companion object {
        const val UDTA = "udta"
        const val META = "meta"
        const val HDLR = "hdlr"
        const val KEYS = "keys"
        const val ILST = "ilst"
        const val FREE = "free"
        const val DATA = "data"
        const val GPS_POINT = "©xyz"
        const val GOOGLE_DATE = "date"
        const val NAME = "name"
        const val LOCATION = "loci"
        const val YEAR = "yrrc"
        const val LOCATION_KEY = "com.apple.quicktime.location.ISO6709"

        const val MAX_TEXT_SIZE = 4096L
        const val LANGUAGE_SIZE = 2L
        const val QUICKTIME_TEXT_HEADER_SIZE = 4L
        const val UDTA_TERMINATOR_SIZE = 4L
        const val FULL_BOX_HEADER_SIZE = 4L
        const val KEY_NAMESPACE_SIZE = 4
        const val KEY_HEADER_SIZE = 8L
        const val DATA_HEADER_SIZE = 8L
        const val DATA_TYPE_UTF8 = 1L
        const val COORDINATES_SIZE = 8
        const val FIXED_POINT_ONE = 65536.0
        const val ROLE_SIZE = 1
    }

    // endregion State

    // region Methods

    fun read(box: Box): List<MetadataItem> {
        return try {
            if (box.type == UDTA) readUserData(box) else readMeta(box)
        } catch (_: MalformedFileException) {
            listOf(unknown(box, box.type))
        }
    }

    fun isXmp(uuid: Box): Boolean {
        reader.seek(uuid.headerEnd)
        return reader.readBytes(xmpUuid.size).contentEquals(xmpUuid)
    }

    // QuickTime allows a 32-bit zero after the last child, which isn't a box.
    private fun userDataChildren(udta: Box): List<Box> {
        return try {
            boxReader.readChildren(udta)
        } catch (_: MalformedFileException) {
            boxReader.read(udta.dataStart, udta.end - UDTA_TERMINATOR_SIZE)
        }
    }

    private fun readUserData(udta: Box): List<MetadataItem> {
        return userDataChildren(udta).flatMap { child ->
            val type = child.type
            when {
                type == META -> readMeta(child)
                type == GPS_POINT -> listOf(gps(child, coordinates(quickTimeText(child))))
                type == LOCATION -> listOf(gps(child, locationCoordinates(child)))
                type == GOOGLE_DATE -> listOf(item(child, dates to MetadataLabel.DATE_TIME_ORIGINAL, text(child.dataStart, child.end)))
                type == YEAR -> listOf(item(child, dates to MetadataLabel.DATE_TIME_ORIGINAL, year(child)))
                type == NAME -> listOf(item(child, other to MetadataLabel.TITLE, text(child.dataStart, child.end)))
                type in quickTimeTags -> listOf(item(child, quickTimeTags.getValue(type), quickTimeText(child)))
                type in threeGppTags -> listOf(item(child, threeGppTags.getValue(type), threeGppText(child)))
                type in vendorBoxes -> listOf(item(child, device to MetadataLabel.VENDOR_DATA, type))
                else -> listOf(unknown(child, type))
            }
        }
    }

    private fun readMeta(meta: Box): List<MetadataItem> {
        val children = boxReader.read(metaChildrenStart(meta), meta.end)
        val keys = children.firstOrNull { it.type == KEYS }?.let { readKeys(it) } ?: emptyList()
        return children.flatMap { child ->
            when (child.type) {
                HDLR, KEYS, FREE -> emptyList()
                ILST -> boxReader.readChildren(child).map { readListItem(it, keys) }
                else -> listOf(unknown(child, child.type))
            }
        }
    }

    // ISO's `meta` is a full box and QuickTime's isn't, so its first child's type tells them apart.
    private fun metaChildrenStart(meta: Box): Long {
        if (meta.end - meta.dataStart >= KEY_HEADER_SIZE) {
            reader.seek(meta.dataStart + FULL_BOX_HEADER_SIZE)
            if (reader.readAscii(HDLR.length) == HDLR) {
                return meta.dataStart
            }
        }
        return boxReader.fullBoxDataStart(meta)
    }

    private fun readKeys(keysBox: Box): List<String> {
        val keys = mutableListOf<String>()
        var position = boxReader.fullBoxDataStart(keysBox) + FULL_BOX_HEADER_SIZE
        while (keysBox.end - position >= KEY_HEADER_SIZE) {
            reader.seek(position)
            val size = reader.readU32()
            if (size < KEY_HEADER_SIZE || size > keysBox.end - position) {
                throw MalformedFileException("Metadata key at offset $position has size $size")
            }
            reader.skip(KEY_NAMESPACE_SIZE.toLong())
            keys += text(reader.position, position + size)
            position += size
        }
        return keys
    }

    // Children of `ilst` are named by a tag, or with `keys`, by a 1-based index stored in the type field.
    private fun readListItem(entry: Box,
                             keys: List<String>): MetadataItem {
        val key = if (keys.isEmpty()) null else keys.getOrNull(keyIndex(entry) - 1)
        val tag = if (keys.isEmpty()) itemListTags[entry.type] else keyTags[key]
        val value = dataValue(entry)
        return when {
            tag == null -> unknownEntry(entry, key ?: entry.type)
            key == LOCATION_KEY -> gps(entry, coordinates(value))
            else -> item(entry, tag, value)
        }
    }

    private fun keyIndex(entry: Box): Int = entry.type.fold(0) { index, char -> (index shl 8) or char.code }

    private fun unknownEntry(entry: Box,
                             name: String): MetadataItem {
        val category = if (name.contains("location")) MetadataCategory.Location else other
        return MetadataItem(category, MetadataLabel.UNKNOWN_BOX, name, entry.end - entry.start)
    }

    private fun dataValue(entry: Box): String {
        val data = boxReader.readChildren(entry).firstOrNull { it.type == DATA } ?: return ""
        if (data.end - data.dataStart < DATA_HEADER_SIZE) {
            return ""
        }
        reader.seek(data.dataStart)
        if (reader.readU32() != DATA_TYPE_UTF8) {
            return ""
        }
        return text(data.dataStart + DATA_HEADER_SIZE, data.end)
    }

    private fun quickTimeText(box: Box): String = text(box.dataStart + QUICKTIME_TEXT_HEADER_SIZE, box.end)

    private fun threeGppText(box: Box): String {
        val bytes = bytes(boxReader.fullBoxDataStart(box) + LANGUAGE_SIZE, box.end)
        val isUtf16 = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        val value = if (isUtf16) String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) else String(bytes, Charsets.UTF_8)
        return value.trimEnd('\u0000')
    }

    private fun year(yrrc: Box): String {
        val start = boxReader.fullBoxDataStart(yrrc)
        if (yrrc.end - start < LANGUAGE_SIZE) {
            return ""
        }
        reader.seek(start)
        return reader.readU16().toString()
    }

    // `loci` holds a name, a role byte, then longitude and latitude as signed 16.16 fixed point.
    private fun locationCoordinates(loci: Box): String {
        val bytes = bytes(boxReader.fullBoxDataStart(loci) + LANGUAGE_SIZE, loci.end)
        val nameEnd = bytes.indexOf(0)
        val start = nameEnd + 1 + ROLE_SIZE
        if (nameEnd < 0 || bytes.size - start < COORDINATES_SIZE) {
            return ""
        }
        val values = ByteBuffer.wrap(bytes, start, COORDINATES_SIZE)
        val longitude = values.int / FIXED_POINT_ONE
        val latitude = values.int / FIXED_POINT_ONE
        return formatCoordinates(latitude, longitude)
    }

    private fun coordinates(iso6709Text: String): String {
        val match = iso6709.find(iso6709Text) ?: return ""
        return formatCoordinates(match.groupValues[1].toDouble(), match.groupValues[2].toDouble())
    }

    // Android zeroes these bytes for apps without ACCESS_MEDIA_LOCATION, so 0, 0 means "hidden", not the Gulf of Guinea.
    private fun formatCoordinates(latitude: Double,
                                  longitude: Double): String {
        if (latitude == 0.0 && longitude == 0.0) {
            return ""
        }
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
    }

    private fun text(start: Long,
                     end: Long): String = String(bytes(start, end), Charsets.UTF_8).trimEnd('\u0000')

    // A value is shown in one row of a list, so a crafted megabyte of text doesn't need reading.
    private fun bytes(start: Long,
                      end: Long): ByteArray {
        if (end <= start) {
            return ByteArray(0)
        }
        reader.seek(start)
        return reader.readBytes(minOf(end - start, MAX_TEXT_SIZE).toInt())
    }

    private fun gps(box: Box,
                    detail: String): MetadataItem {
        return MetadataItem(MetadataCategory.Location, MetadataLabel.GPS, detail, box.end - box.start)
    }

    private fun item(box: Box,
                     tag: Pair<MetadataCategory, String>,
                     detail: String): MetadataItem {
        return MetadataItem(tag.first, tag.second, detail, box.end - box.start)
    }

    private fun unknown(box: Box,
                        type: String): MetadataItem {
        return MetadataItem(other, MetadataLabel.UNKNOWN_BOX, type, box.end - box.start)
    }

    // endregion Methods
}
