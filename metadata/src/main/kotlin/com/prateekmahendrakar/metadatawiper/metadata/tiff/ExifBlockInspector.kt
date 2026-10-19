package com.prateekmahendrakar.metadatawiper.metadata.tiff

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader

/*
 * Lists what an EXIF block holds, wherever the container keeps it (JPEG APP1, PNG eXIf, WebP
 * EXIF). The summary names the tags worth naming, but a block can hold plenty it doesn't, so
 * the block itself is reported too. The one exception is the minimal EXIF the handlers write,
 * which must re-inspect as orientation only for verification to pass.
 */
object ExifBlockInspector {

    // region State

    val DAMAGED_WARNING = MetadataWarning(MetadataWarningKind.DamagedExif)

    // JPEG always uses this prefix; PNG and WebP shouldn't, but some writers add it anyway.
    private val exifIdentifier = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00) // "Exif\0"
    private const val EXIF_IDENTIFIER_SIZE = 6L

    // endregion State

    // region Methods

    fun inspect(reader: BinaryReader,
                blockStart: Long,
                blockLength: Long): ExifInspection {
        val blockItem = MetadataItem(MetadataCategory.Other, MetadataLabel.EXIF_BLOCK, byteSize = blockLength)
        val tiffOffset = if (hasExifIdentifier(reader, blockStart, blockLength)) EXIF_IDENTIFIER_SIZE else 0L
        val tiffStart = blockStart + tiffOffset
        val tiffLength = blockLength - tiffOffset

        // A damaged block is removed anyway, so it shouldn't stop the rest of the file being cleaned.
        val summary = try {
            TiffSummaryReader(reader, tiffStart, tiffLength).read()
        } catch (_: MalformedFileException) {
            return ExifInspection(listOf(blockItem), orientation = null, isDamaged = true)
        }

        if (isMinimalExif(reader, tiffStart, tiffLength, summary.orientation)) {
            return ExifInspection(summary.items, summary.orientation, isDamaged = false)
        }
        return ExifInspection(summary.items + blockItem, summary.orientation, isDamaged = false)
    }

    private fun hasExifIdentifier(reader: BinaryReader,
                                  blockStart: Long,
                                  blockLength: Long): Boolean {
        if (blockLength < EXIF_IDENTIFIER_SIZE) {
            return false
        }
        reader.seek(blockStart)
        return reader.readBytes(exifIdentifier.size).contentEquals(exifIdentifier)
    }

    private fun isMinimalExif(reader: BinaryReader,
                              tiffStart: Long,
                              tiffLength: Long,
                              orientation: Int?): Boolean {
        if (orientation == null || orientation !in 1..8 || tiffLength != MinimalExifWriter.TIFF_SIZE.toLong()) {
            return false
        }
        reader.seek(tiffStart)
        return reader.readBytes(MinimalExifWriter.TIFF_SIZE).contentEquals(MinimalExifWriter.tiff(orientation))
    }

    // endregion Methods
}
