package com.prateekmahendrakar.metadatawiper.metadata

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FormatDetector {

    // region State

    /*
     * Most signatures fit in the first few bytes, and an ISO BMFF `ftyp` brand list in the first
     * 64. MPEG transport streams are the outlier: they're recognised by the 0x47 sync byte
     * repeating every 188 bytes (192 for M2TS), which takes 389 bytes to see three times.
     */
    const val HEADER_SIZE = 512

    private val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val gif87Signature = ascii("GIF87a")
    private val gif89Signature = ascii("GIF89a")
    private val riffSignature = ascii("RIFF")
    private val webPSignature = ascii("WEBP")
    private val aviSignature = ascii("AVI ")
    private val ftypSignature = ascii("ftyp")
    private val bmpSignature = ascii("BM")
    private val tiffLittleEndianSignature = byteArrayOf(0x49, 0x49, 0x2A, 0x00)
    private val tiffBigEndianSignature = byteArrayOf(0x4D, 0x4D, 0x00, 0x2A)
    private val ebmlSignature = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val mpegPsSignature = byteArrayOf(0x00, 0x00, 0x01, 0xBA.toByte())

    private val avifBrands = setOf("avif", "avis")
    private val heifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
    private val mp4Brands = setOf("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
                                  "mp41", "mp42", "mp71", "avc1", "dash", "M4V ", "M4VH", "M4VP",
                                  "mmp4", "XAVC", "MSNV", "f4v ")
    private const val QUICKTIME_BRAND = "qt  "
    private val threeGppBrandPrefixes = listOf("3gp", "3g2")

    // Audio is Phase 3, and Canon's CR3 raw format shares the container; neither may pass as video.
    private val excludedMajorBrands = setOf("M4A ", "M4B ", "M4P ", "F4A ", "F4B ", "crx ")

    // QuickTime files that predate `ftyp` start straight with one of these.
    private val quickTimeLeadingBoxes = setOf("moov", "mdat", "wide", "free", "skip", "pnot")

    // Header sizes of BITMAPCOREHEADER, BITMAPINFOHEADER and its V2–V5 successors.
    private val bmpInfoHeaderSizes = setOf(12L, 40L, 52L, 56L, 108L, 124L)

    private const val RIFF_FORM_OFFSET = 8
    private const val FTYP_OFFSET = 4
    private const val FIRST_BRAND_OFFSET = 8
    private const val COMPATIBLE_BRANDS_OFFSET = 16
    private const val BRAND_SIZE = 4
    private const val BOX_HEADER_SIZE = 8L
    private const val LARGE_BOX_SIZE = 1L
    private const val BMP_INFO_HEADER_SIZE_OFFSET = 14

    private const val EBML_DOC_TYPE_ID = 0x4282L
    private const val MATROSKA_DOC_TYPE = "matroska"
    private const val WEBM_DOC_TYPE = "webm"

    private const val TS_SYNC_BYTE: Byte = 0x47
    private const val TS_PACKET_SIZE = 188
    private const val M2TS_PACKET_SIZE = 192
    private const val M2TS_SYNC_OFFSET = 4
    private const val TS_SYNC_COUNT = 3

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
        if (header.hasSignature(riffSignature) && header.hasSignature(webPSignature, RIFF_FORM_OFFSET)) {
            return FileFormat.WebP
        }
        if (header.hasSignature(riffSignature) && header.hasSignature(aviSignature, RIFF_FORM_OFFSET)) {
            return FileFormat.Avi
        }
        if (header.hasSignature(ftypSignature, FTYP_OFFSET)) {
            return detectIsoBmff(header)
        }
        if (hasLeadingQuickTimeBox(header)) {
            return FileFormat.QuickTime
        }
        if (header.hasSignature(ebmlSignature)) {
            return detectMatroska(header)
        }
        if (header.hasSignature(bmpSignature) && hasBmpInfoHeader(header)) {
            return FileFormat.Bmp
        }
        if (header.hasSignature(tiffLittleEndianSignature) || header.hasSignature(tiffBigEndianSignature)) {
            return FileFormat.Tiff
        }
        if (header.hasSignature(mpegPsSignature)) {
            return FileFormat.MpegPs
        }
        if (hasTsSync(header, 0, TS_PACKET_SIZE) || hasTsSync(header, M2TS_SYNC_OFFSET, M2TS_PACKET_SIZE)) {
            return FileFormat.MpegTs
        }
        return FileFormat.Unknown
    }

    // HEIF, AVIF, MP4, MOV and 3GP all share the `ftyp` signature, so the brands decide.
    private fun detectIsoBmff(header: ByteArray): FileFormat {
        val brands = readBrands(header)
        if (brands.any { it in avifBrands }) {
            return FileFormat.Avif
        }
        if (brands.any { it in heifBrands }) {
            return FileFormat.Heif
        }

        val majorBrand = brands.firstOrNull() ?: return FileFormat.Unknown
        if (majorBrand in excludedMajorBrands) {
            return FileFormat.Unknown
        }
        if (majorBrand == QUICKTIME_BRAND) {
            return FileFormat.QuickTime
        }
        if (threeGppBrandPrefixes.any { majorBrand.startsWith(it) }) {
            return FileFormat.ThreeGpp
        }
        if (brands.any { it in mp4Brands }) {
            return FileFormat.Mp4
        }
        if (QUICKTIME_BRAND in brands) {
            return FileFormat.QuickTime
        }
        return FileFormat.Unknown
    }

    // The major brand, then the compatible brands, skipping the minor version between them.
    private fun readBrands(header: ByteArray): List<String> {
        val boxSize = readUInt32(header, 0, ByteOrder.BIG_ENDIAN)
        val brandsEnd = minOf(boxSize, header.size.toLong()).toInt()
        val brandOffsets = listOf(FIRST_BRAND_OFFSET) + (COMPATIBLE_BRANDS_OFFSET until brandsEnd step BRAND_SIZE)
        return brandOffsets.filter { it + BRAND_SIZE <= brandsEnd }
                           .map { String(header, it, BRAND_SIZE, Charsets.ISO_8859_1) }
    }

    private fun hasLeadingQuickTimeBox(header: ByteArray): Boolean {
        if (header.size < BOX_HEADER_SIZE) {
            return false
        }
        val size = readUInt32(header, 0, ByteOrder.BIG_ENDIAN)
        val type = String(header, FTYP_OFFSET, BRAND_SIZE, Charsets.ISO_8859_1)
        return type in quickTimeLeadingBoxes && (size >= BOX_HEADER_SIZE || size == LARGE_BOX_SIZE)
    }

    /*
     * The EBML header's DocType names the flavour. It's a short master element right at the
     * start, so the whole header fits in what we read; if DocType isn't there, the file isn't
     * one we recognise.
     */
    private fun detectMatroska(header: ByteArray): FileFormat {
        var position = ebmlSignature.size
        val headerSize = readVint(header, position) ?: return FileFormat.Unknown
        position += headerSize.length
        val end = minOf(position.toLong() + headerSize.value, header.size.toLong()).toInt()

        while (position < end) {
            val id = readElementId(header, position) ?: return FileFormat.Unknown
            position += id.length
            val size = readVint(header, position) ?: return FileFormat.Unknown
            position += size.length
            if (size.value > end - position) {
                return FileFormat.Unknown
            }
            if (id.value == EBML_DOC_TYPE_ID) {
                // EBML strings may be padded with zeros.
                val docType = String(header, position, size.value.toInt(), Charsets.US_ASCII).trimEnd('\u0000')
                return when (docType) {
                    MATROSKA_DOC_TYPE -> FileFormat.Matroska
                    WEBM_DOC_TYPE -> FileFormat.WebM
                    else -> FileFormat.Unknown
                }
            }
            position += size.value.toInt()
        }
        return FileFormat.Unknown
    }

    // An element ID keeps its length-marker bits; IDs are at most 4 bytes long.
    private fun readElementId(bytes: ByteArray,
                              offset: Int): Vint? {
        val vint = readVint(bytes, offset) ?: return null
        if (vint.length > 4) {
            return null
        }
        var id = 0L
        for (index in 0 until vint.length) {
            id = (id shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        return Vint(id, vint.length)
    }

    // The count of leading zero bits in the first byte gives the length; the marker bit is dropped from the value.
    private fun readVint(bytes: ByteArray,
                         offset: Int): Vint? {
        if (offset >= bytes.size) {
            return null
        }
        val first = bytes[offset].toInt() and 0xFF
        if (first == 0) {
            return null
        }
        val length = Integer.numberOfLeadingZeros(first) - 23
        if (offset + length > bytes.size) {
            return null
        }
        var value = (first and (0xFF shr length)).toLong()
        for (index in 1 until length) {
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        return Vint(value, length)
    }

    private fun hasTsSync(header: ByteArray,
                          firstOffset: Int,
                          packetSize: Int): Boolean {
        return (0 until TS_SYNC_COUNT).all {
            val offset = firstOffset + it * packetSize
            offset < header.size && header[offset] == TS_SYNC_BYTE
        }
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

    private class Vint(val value: Long,
                       val length: Int)
}
