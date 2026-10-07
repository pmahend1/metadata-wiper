package com.prateekmahendrakar.metadatawiper.metadata.tiff

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Builds the smallest valid EXIF that still carries orientation: a little-endian TIFF
 * header and one IFD holding a single Orientation entry. Used when stripping would
 * otherwise rotate the picture, because the pixel data is stored unrotated.
 */
object MinimalExifWriter {

    // region State

    const val TIFF_SIZE = 26

    private val jpegExifIdentifier = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00) // "Exif\0\0"

    private const val FIRST_IFD_OFFSET = 8
    private const val ENTRY_COUNT: Short = 1
    private const val NO_NEXT_IFD = 0

    // endregion State

    // region Methods

    // Raw TIFF, as stored in a PNG `eXIf` or WebP `EXIF` chunk.
    fun tiff(orientation: Int): ByteArray {
        require(orientation in 1..8) { "Orientation must be 1–8, was $orientation" }
        return ByteBuffer.allocate(TIFF_SIZE)
                         .order(ByteOrder.LITTLE_ENDIAN)
                         .put(TiffTag.LITTLE_ENDIAN_MARK)
                         .putShort(TiffTag.MAGIC.toShort())
                         .putInt(FIRST_IFD_OFFSET)
                         .putShort(ENTRY_COUNT)
                         .putShort(TiffTag.ORIENTATION.toShort())
                         .putShort(TiffTag.TYPE_SHORT.toShort())
                         .putInt(1)
                         .putShort(orientation.toShort())
                         .putShort(0)
                         .putInt(NO_NEXT_IFD)
                         .array()
    }

    // TIFF prefixed with "Exif\0\0", as stored in a JPEG APP1 segment (marker and length excluded).
    fun jpegApp1Payload(orientation: Int): ByteArray = jpegExifIdentifier + tiff(orientation)

    // endregion Methods
}
