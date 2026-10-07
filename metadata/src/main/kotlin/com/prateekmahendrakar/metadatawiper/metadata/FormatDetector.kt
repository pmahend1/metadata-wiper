package com.prateekmahendrakar.metadatawiper.metadata

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FormatDetector {

    // region State

    /*
     * 32 bytes is enough for every fixed signature, but an ISO BMFF `ftyp` box lists its
     * compatible brands after the major brand, and AVIF files written with a `mif1` major
     * brand only reveal `avif` further in. 64 bytes covers the brand lists seen in practice.
     */
    const val HEADER_SIZE = 64

    private val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val gif87Signature = ascii("GIF87a")
    private val gif89Signature = ascii("GIF89a")
    private val riffSignature = ascii("RIFF")
    private val webPSignature = ascii("WEBP")
    private val ftypSignature = ascii("ftyp")
    private val bmpSignature = ascii("BM")
    private val tiffLittleEndianSignature = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
    private val tiffBigEndianSignature = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)

    private val avifBrands = setOf("avif", "avis")
    private val heifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")

    // Header sizes of BITMAPCOREHEADER, BITMAPINFOHEADER and its V2–V5 successors.
    private val bmpInfoHeaderSizes = setOf(12L, 40L, 52L, 56L, 108L, 124L)

    private const val WEBP_OFFSET = 8
    private const val FTYP_OFFSET = 4
    private const val FIRST_BRAND_OFFSET = 8
    private const val COMPATIBLE_BRANDS_OFFSET = 16
    private const val BRAND_SIZE = 4
    private const val BMP_INFO_HEADER_SIZE_OFFSET = 14

    // endregion State

    // region Methods

    fun detect(header: ByteArray): FileFormat {
        if (header.hasSignature(jpegSignature)) {
            return FileFormat.Jpeg
        }
        if (header.hasSignature(pngSignature)) {
            return FileFormat.Png
        }
        if (header.hasSignature(gif87Signature) || header.hasSignature(gif89Signature)) {
            return FileFormat.Gif
        }
        if (header.hasSignature(riffSignature) && header.hasSignature(webPSignature, WEBP_OFFSET)) {
            return FileFormat.WebP
        }
        if (header.hasSignature(ftypSignature, FTYP_OFFSET)) {
            return detectIsoBmff(header)
        }
        if (header.hasSignature(bmpSignature) && hasBmpInfoHeader(header)) {
            return FileFormat.Bmp
        }
        if (header.hasSignature(tiffLittleEndianSignature) || header.hasSignature(tiffBigEndianSignature)) {
            return FileFormat.Tiff
        }
        return FileFormat.Unknown
    }

    // MP4, MOV and other ISO BMFF files share the `ftyp` signature, so the brand decides.
    private fun detectIsoBmff(header: ByteArray): FileFormat {
        val brands = readBrands(header)
        if (brands.any { it in avifBrands }) {
            return FileFormat.Avif
        }
        if (brands.any { it in heifBrands }) {
            return FileFormat.Heif
        }
        return FileFormat.Unknown
    }

    // The major brand, then the compatible brands, skipping the minor version between them.
    private fun readBrands(header: ByteArray): List<String> {
        val boxSize = readUInt32(header, 0, ByteOrder.BIG_ENDIAN)
        val brandsEnd = minOf(boxSize, header.size.toLong()).toInt()
        val brandOffsets = listOf(FIRST_BRAND_OFFSET) + (COMPATIBLE_BRANDS_OFFSET until brandsEnd step BRAND_SIZE)
        return brandOffsets.filter { it + BRAND_SIZE <= brandsEnd }
                           .map { String(header, it, BRAND_SIZE, Charsets.US_ASCII) }
    }

    private fun hasBmpInfoHeader(header: ByteArray): Boolean {
        if (header.size < BMP_INFO_HEADER_SIZE_OFFSET + 4) {
            return false
        }
        return readUInt32(header, BMP_INFO_HEADER_SIZE_OFFSET, ByteOrder.LITTLE_ENDIAN) in bmpInfoHeaderSizes
    }

    private fun readUInt32(bytes: ByteArray,
                           offset: Int,
                           order: ByteOrder): Long {
        return ByteBuffer.wrap(bytes, offset, 4).order(order).int.toLong() and 0xFFFFFFFFL
    }

    private fun ByteArray.hasSignature(signature: ByteArray,
                                       offset: Int = 0): Boolean {
        if (size < offset + signature.size) {
            return false
        }
        return signature.indices.all { this[offset + it] == signature[it] }
    }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

    // endregion Methods
}
