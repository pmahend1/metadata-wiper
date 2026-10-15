package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BlankedRange
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.ItemLocation
import com.prateekmahendrakar.metadatawiper.metadata.tiff.ExifBlockInspector
import java.nio.ByteOrder

/*
 * Works out what a HEIF or AVIF file holds and which bytes a strip overwrites. Nothing moves:
 * metadata payloads are blanked where they are, and boxes that can't be blanked meaningfully
 * become `free` boxes of the same size, so every `iloc` offset stays valid.
 *
 * Image items and the properties needed to render them are kept. Anything kept that can carry
 * identifying data but can't be removed safely is named in a warning rather than reported as
 * an item, because a reported item that survives a strip fails verification.
 */
class HeifStripPlanner(private val reader: BinaryReader,
                       private val options: StripOptions) {

    // region State

    private val boxReader = BoxReader(reader)

    private val items = mutableListOf<MetadataItem>()
    private val warnings = linkedSetOf<String>()
    private val blanked = mutableListOf<BlankedRange>()
    private val imageData = mutableListOf<FileSpan>()

    private val keptTopLevelTypes = setOf(FTYP, META, MDAT, FREE, SKIP)

    private val structuralMetaTypes = setOf("hdlr", "pitm", "iloc", "iinf", "iref", "iprp", "idat", "dinf", "grpl", FREE, SKIP)

    private val imageItemTypes = setOf("hvc1", "hvt1", "lhv1", "av01", "avc1", "vvc1", "vvi1", "jpeg", "j2k1", "unci",
                                       "grid", "iovl", "iden", "tmap", "mski")

    // Properties that only describe how to decode or display the image.
    private val structuralPropertyTypes = setOf("hvcC", "lhvC", "av1C", "avcC", "vvcC", "vvnC", "jpgC", "j2kH", "ispe",
                                                "pixi", "rloc", "clap", "irot", "imir", "lsel", "a1op", "a1lx", "auxC",
                                                "pasp", "clli", "mdcv", "cclv", "amve", "reve", "cmin", "cmex", "uncC",
                                                "cmpd", "cpat", "cloc", "splz", "sbpm", "snuc", "cdef", "iscl", "tols",
                                                "oinf", "elng", FREE, SKIP)

    // Full-box properties whose fields are all text or times, so zeroing them leaves an empty but valid property.
    private val descriptivePropertyItems = mapOf("udes" to (MetadataCategory.Other to "Image description"),
                                                 "altt" to (MetadataCategory.Other to "Alternative text"),
                                                 "crtt" to (MetadataCategory.DateTime to "Creation time"),
                                                 "mdft" to (MetadataCategory.DateTime to "Modification time"),
                                                 "itai" to (MetadataCategory.DateTime to "Timestamp"))

    private val iccColourTypes = setOf("prof", "rICC")

    private val alphaAuxiliaryTypes = setOf("urn:mpeg:mpegB:cicp:systems:auxiliary:alpha", "urn:mpeg:hevc:2015:auxid:1")

    companion object {
        const val THUMBNAIL_WARNING = "The preview thumbnail was kept, because removing it can stop the image from opening."
        const val AUXILIARY_WARNING = "Auxiliary images, such as a depth map or HDR gain map, were kept."
        const val SEQUENCE_WARNING = "This is an image sequence. Its track timestamps can't be removed yet and were kept."

        private const val FTYP = "ftyp"
        private const val META = "meta"
        private const val MDAT = "mdat"
        private const val MOOV = "moov"
        private const val FREE = "free"
        private const val SKIP = "skip"
        private const val MINI = "mini"
        private const val MOTION_PHOTO_VIDEO = "mpvd"
        private const val XML = "xml "
        private const val BINARY_XML = "bxml"
        private const val COLR = "colr"
        private const val AUXC = "auxC"
        private const val THMB = "thmb"
        private const val AUXL = "auxl"

        private const val EXIF = "Exif"
        private const val MIME = "mime"
        private const val URI = "uri "
        private const val REGIONS = "rgan"
        private const val XMP_CONTENT_TYPE = "application/rdf+xml"

        private const val SPACE = 0x20.toByte()
        private const val TYPE_OFFSET = 4L
        private const val TYPE_SIZE = 4L
        private const val TIFF_HEADER_OFFSET_SIZE = 4L

        // Apple's layout: offset 6 to the TIFF header, "Exif\0\0", then an empty TIFF. Android rejects offsets below 6.
        private val exifBlank = byteArrayOf(0, 0, 0, 6, 0x45, 0x78, 0x69, 0x66, 0, 0,
                                            0x49, 0x49, 0x2A, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        // An empty XMP document; the spaces that pad it to length are allowed after an XML root element.
        private val xmpBlank = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>".toByteArray(Charsets.US_ASCII)

        private val freeType = FREE.toByteArray(Charsets.US_ASCII)
    }

    // endregion State

    // region Methods

    fun plan(): HeifStripPlan {
        val topLevel = boxReader.read(0, reader.size)
        if (topLevel.firstOrNull()?.type != FTYP) {
            throw MalformedFileException("HEIF file doesn't start with an 'ftyp' box")
        }
        val meta = HeifMetaReader(reader, boxReader).read(metaBox(topLevel))
        val mediaData = topLevel.filter { it.type == MDAT }.map { FileSpan(it.dataStart, it.end) }

        planTopLevel(topLevel)
        planMetaChildren(meta)
        planItems(meta, mediaData)
        planProperties(meta)
        planReferences(meta)
        return HeifStripPlan(items.toList(), warnings.toList(), checkedBlanked())
    }

    private fun metaBox(topLevel: List<Box>): Box {
        val metas = topLevel.filter { it.type == META }
        if (metas.isEmpty() && topLevel.any { it.type == MINI }) {
            throw MalformedFileException("Compact HEIF files (with a 'mini' box) aren't supported yet")
        }
        if (metas.size != 1) {
            throw MalformedFileException("HEIF file has ${metas.size} 'meta' boxes instead of one")
        }
        return metas.single()
    }

    // region Boxes

    private fun planTopLevel(topLevel: List<Box>) {
        for (box in topLevel) {
            when (box.type) {
                in keptTopLevelTypes -> continue
                MOOV -> warnings += SEQUENCE_WARNING
                MOTION_PHOTO_VIDEO -> removeBox(box, MetadataCategory.EmbeddedMedia, "Motion photo video")
                else -> removeBox(box, MetadataCategory.Other, "Unknown box", box.type)
            }
        }
    }

    private fun planMetaChildren(meta: HeifMeta) {
        val unknownTypes = mutableSetOf<String>()
        for (box in meta.children) {
            when (box.type) {
                in structuralMetaTypes -> continue
                XML, BINARY_XML -> removeBox(box, MetadataCategory.Other, "XML metadata")
                else -> unknownTypes += box.type
            }
        }
        if (unknownTypes.isNotEmpty()) {
            warnings += "Boxes this app doesn't recognise (${unknownTypes.joinToString()}) were left in place."
        }
    }

    private fun removeBox(box: Box,
                          category: MetadataCategory,
                          label: String,
                          detail: String = "") {
        items += MetadataItem(category, label, detail, box.end - box.start)
        blanked += freed(box)
    }

    // The same box as `free`, zeroed after its size fields, including a `uuid` box's extended type.
    private fun freed(box: Box): List<BlankedRange> {
        return listOf(BlankedRange(FileSpan(box.start + TYPE_OFFSET, box.start + TYPE_OFFSET + TYPE_SIZE), freeType),
                      BlankedRange(FileSpan(box.headerEnd, box.end)))
    }

    // endregion Boxes

    // region Items

    private fun planItems(meta: HeifMeta,
                          mediaData: List<FileSpan>) {
        val unknownTypes = mutableSetOf<String>()
        var namedItems = 0
        var nameBytes = 0L
        for (item in meta.items) {
            when (item.type) {
                EXIF -> planExif(metadataSpans(item, meta, mediaData))
                MIME -> planMime(item, metadataSpans(item, meta, mediaData))
                URI -> planOpaque(metadataSpans(item, meta, mediaData), MetadataCategory.Other, "Linked data")
                REGIONS -> planOpaque(metadataSpans(item, meta, mediaData), MetadataCategory.People, "Region annotations")
                in imageItemTypes -> imageData += imageSpans(item, meta)
                else -> {
                    unknownTypes += item.type
                    imageData += imageSpans(item, meta)
                }
            }

            // Names never affect decoding. Spaces, not zeros, keep the next string in the entry where it was.
            val name = BlankedRange(item.name, fill = SPACE)
            blanked += name
            if (name.isAlreadyBlank(reader) == false) {
                namedItems++
                nameBytes += item.name.length
            }
        }

        if (namedItems > 0) {
            items += MetadataItem(MetadataCategory.Other, "Item names", namedItems.toString(), nameBytes)
        }
        if (unknownTypes.isNotEmpty()) {
            warnings += "Items of a type this app doesn't recognise (${unknownTypes.joinToString()}) were left in place."
        }
    }

    private fun planExif(spans: List<FileSpan>) {
        val ranges = BlankedRange.across(spans, exifBlank)
        blanked += ranges
        if (ranges.all { it.isAlreadyBlank(reader) } == false) {
            items += inspectExif(spans)
        }
    }

    /*
     * HEIF readers take orientation from the `irot` and `imir` properties, which are kept, and
     * ignore the EXIF copy. Reporting that copy as removed would wrongly suggest the picture
     * will turn.
     */
    private fun inspectExif(spans: List<FileSpan>): List<MetadataItem> {
        val length = spans.sumOf { it.length }
        val block = MetadataItem(MetadataCategory.Other, "EXIF block", byteSize = length)
        if (spans.size != 1) {
            return listOf(block)
        }
        val span = spans.single()
        if (length < TIFF_HEADER_OFFSET_SIZE) {
            warnings += ExifBlockInspector.DAMAGED_WARNING
            return listOf(block)
        }
        reader.seek(span.start)
        reader.byteOrder = ByteOrder.BIG_ENDIAN
        val tiffOffset = reader.readU32()
        if (tiffOffset > length - TIFF_HEADER_OFFSET_SIZE) {
            warnings += ExifBlockInspector.DAMAGED_WARNING
            return listOf(block)
        }

        val tiffStart = span.start + TIFF_HEADER_OFFSET_SIZE + tiffOffset
        val exif = ExifBlockInspector.inspect(reader, tiffStart, span.end - tiffStart)
        if (exif.isDamaged) {
            warnings += ExifBlockInspector.DAMAGED_WARNING
        }
        return exif.items.filter { it.category != MetadataCategory.Orientation }
    }

    // Plain XMP becomes an empty XMP document; anything else, including compressed XMP, is zeroed.
    private fun planMime(item: HeifItem,
                         spans: List<FileSpan>) {
        val isXmp = item.contentType == XMP_CONTENT_TYPE
        val ranges = if (isXmp && item.contentEncoding.isEmpty()) {
            BlankedRange.across(spans, xmpBlank, SPACE)
        } else {
            BlankedRange.across(spans)
        }
        blanked += ranges
        if (ranges.all { it.isAlreadyBlank(reader) }) {
            return
        }
        items += if (isXmp) {
            MetadataItem(MetadataCategory.Other, "XMP", byteSize = spans.sumOf { it.length })
        } else {
            MetadataItem(MetadataCategory.Other, "Embedded data", item.contentType, spans.sumOf { it.length })
        }
    }

    // An all-zero region item is a valid one with no regions.
    private fun planOpaque(spans: List<FileSpan>,
                           category: MetadataCategory,
                           label: String) {
        val ranges = BlankedRange.across(spans)
        blanked += ranges
        if (ranges.all { it.isAlreadyBlank(reader) } == false) {
            items += MetadataItem(category, label, byteSize = spans.sumOf { it.length })
        }
    }

    // A metadata item's bytes must be in this file, inside `mdat` or `idat`, so blanking them can't damage the structure.
    private fun metadataSpans(item: HeifItem,
                              meta: HeifMeta,
                              mediaData: List<FileSpan>): List<FileSpan> {
        val location = meta.locations[item.id] ?: return emptyList()
        if (location.dataReferenceIndex != 0) {
            throw MalformedFileException("HEIF metadata item ${item.id} is stored in another file, which isn't supported")
        }
        val container = when (location.constructionMethod) {
            ItemLocation.FILE_OFFSET -> FileSpan(0, reader.size)
            ItemLocation.IDAT_OFFSET -> meta.idat?.let { FileSpan(it.dataStart, it.end) }
                                        ?: throw MalformedFileException("HEIF metadata item ${item.id} is in a missing 'idat' box")
            else -> throw MalformedFileException("HEIF metadata item ${item.id} uses construction method " +
                                                 "${location.constructionMethod}, which isn't supported")
        }
        val spans = resolve(location, container)
                    ?: throw MalformedFileException("HEIF metadata item ${item.id} lies outside its data")
        val allowed = if (location.constructionMethod == ItemLocation.IDAT_OFFSET) listOf(container) else mediaData
        if (spans.any { span -> allowed.none { it.contains(span) } }) {
            throw MalformedFileException("HEIF metadata item ${item.id} lies outside the media data")
        }
        return spans
    }

    // Best effort: an image item that can't be located is copied as-is, and only matters for the overlap check.
    private fun imageSpans(item: HeifItem,
                           meta: HeifMeta): List<FileSpan> {
        val location = meta.locations[item.id] ?: return emptyList()
        if (location.dataReferenceIndex != 0) {
            return emptyList()
        }
        val container = when (location.constructionMethod) {
            ItemLocation.FILE_OFFSET -> FileSpan(0, reader.size)
            ItemLocation.IDAT_OFFSET -> meta.idat?.let { FileSpan(it.dataStart, it.end) } ?: return emptyList()
            else -> return emptyList()
        }
        return resolve(location, container) ?: emptyList()
    }

    // Null when an extent falls outside [container]. Written to avoid overflow with 64-bit offsets.
    private fun resolve(location: ItemLocation,
                        container: FileSpan): List<FileSpan>? {
        return location.extents.map { extent ->
            val available = container.length
            if (location.baseOffset > available || extent.offset > available - location.baseOffset) {
                return null
            }
            val start = container.start + location.baseOffset + extent.offset
            val length = if (extent.length == 0L) container.end - start else extent.length
            if (length > container.end - start) {
                return null
            }
            FileSpan(start, start + length)
        }
    }

    // endregion Items

    // region Properties

    private fun planProperties(meta: HeifMeta) {
        val unknownTypes = mutableSetOf<String>()
        for (property in meta.properties) {
            val box = property.box
            when (box.type) {
                COLR -> planColour(property, meta)
                in descriptivePropertyItems -> planDescriptive(box)
                in structuralPropertyTypes -> continue
                else -> unknownTypes += box.type
            }
        }
        if (unknownTypes.isNotEmpty()) {
            warnings += "Image properties this app doesn't recognise (${unknownTypes.joinToString()}) were left in place."
        }
    }

    /*
     * An ICC profile is removed by turning its `colr` box into `free`, so the property indices
     * `ipma` uses don't shift. A reader would refuse an image whose essential property it
     * doesn't understand, so the essential flag on the profile is cleared too.
     */
    private fun planColour(property: HeifProperty,
                           meta: HeifMeta) {
        if (property.subtype !in iccColourTypes) {
            return
        }
        val box = property.box
        items += MetadataItem(MetadataCategory.ColorProfile, "ICC profile", byteSize = box.end - box.dataStart - TYPE_SIZE)
        if (options.keepColorProfile) {
            return
        }
        blanked += freed(box)
        for (association in meta.associations) {
            if (association.propertyIndex == property.index && association.isEssential) {
                val span = FileSpan(association.flagOffset, association.flagOffset + 1)
                blanked += BlankedRange(span, byteArrayOf((association.flagByte and 0x7F).toByte()))
            }
        }
    }

    private fun planDescriptive(box: Box) {
        val range = BlankedRange(FileSpan(boxReader.fullBoxDataStart(box), box.end))
        blanked += range
        if (range.isAlreadyBlank(reader) == false) {
            val (category, label) = descriptivePropertyItems.getValue(box.type)
            items += MetadataItem(category, label, byteSize = range.span.length)
        }
    }

    // endregion Properties

    private fun planReferences(meta: HeifMeta) {
        for (reference in meta.references) {
            when (reference.type) {
                THMB -> warnings += THUMBNAIL_WARNING
                AUXL -> if (isAlpha(reference.fromId, meta) == false) warnings += AUXILIARY_WARNING
            }
        }
    }

    private fun isAlpha(itemId: Long,
                        meta: HeifMeta): Boolean {
        val indices = meta.associations.filter { it.itemId == itemId }.map { it.propertyIndex }.toSet()
        return meta.properties.any { it.index in indices && it.box.type == AUXC && it.subtype in alphaAuxiliaryTypes }
    }

    // Overlapping overwrites would depend on order, and overwriting image data would corrupt the picture.
    private fun checkedBlanked(): List<BlankedRange> {
        val sorted = blanked.filter { it.span.length > 0 }.sortedBy { it.span.start }
        for ((previous, next) in sorted.zipWithNext()) {
            if (previous.span.overlaps(next.span)) {
                throw MalformedFileException("HEIF metadata at offset ${next.span.start} overlaps other metadata")
            }
        }
        for (range in sorted) {
            if (imageData.any { it.overlaps(range.span) }) {
                throw MalformedFileException("HEIF metadata at offset ${range.span.start} overlaps image data")
            }
        }
        return sorted
    }

    // endregion Methods
}
