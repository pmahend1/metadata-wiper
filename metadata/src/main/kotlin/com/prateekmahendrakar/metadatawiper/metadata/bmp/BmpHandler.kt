package com.prateekmahendrakar.metadatawiper.metadata.bmp

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import kotlin.math.abs

/*
 * BMP has no metadata segment. The one thing worth removing is a V5 header's color profile:
 * a linked profile is a file path that can name the user (C:\Users\<name>\…), and an embedded
 * one goes when the options say so. Either way the profile bytes are zeroed in place and the
 * color space set to sRGB, so the file keeps its size and no offset moves.
 */
class BmpHandler : FormatHandler {

    // region State

    override val format = FileFormat.Bmp

    // BITMAPCOREHEADER, BITMAPINFOHEADER and its V2–V5 successors.
    private val infoHeaderSizes = setOf(12L, 40L, 52L, 56L, 108L, 124L)

    // Only these store rows uncompressed, so only for these can the pixel data size be computed.
    private val uncompressedCompressions = setOf(BI_RGB, BI_BITFIELDS, BI_ALPHABITFIELDS)

    private companion object {
        const val FILE_HEADER_SIZE = 14L
        const val CORE_HEADER_SIZE = 12L
        const val V5_HEADER_SIZE = 124L
        const val MIN_FILE_SIZE = FILE_HEADER_SIZE + 4
        const val PIXEL_OFFSET_POSITION = 10L
        const val IMAGE_FIELDS_POSITION = FILE_HEADER_SIZE + 4
        const val PLANES_FIELD = 2L

        const val CS_TYPE_POSITION = FILE_HEADER_SIZE + 56
        const val PROFILE_DATA_POSITION = FILE_HEADER_SIZE + 112

        const val LCS_SRGB = 0x73524742L          // 'sRGB'
        const val PROFILE_LINKED = 0x4C494E4BL    // 'LINK'
        const val PROFILE_EMBEDDED = 0x4D424544L  // 'MBED'

        const val BI_RGB = 0L
        const val BI_BITFIELDS = 3L
        const val BI_ALPHABITFIELDS = 6L

        const val MAX_PATH_LENGTH = 260
        const val ZERO_CHUNK_SIZE = 64 * 1024
    }

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val reader = BinaryReader(input)
        return InspectionResult(format, findItems(reader, readProfile(reader)))
    }

    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val profile = readProfile(reader)
        val items = findItems(reader, profile)

        if (profile == null || (profile.isLinked == false && options.keepColorProfile)) {
            reader.copyTo(output, 0, reader.size)
        } else {
            reader.copyTo(output, 0, CS_TYPE_POSITION)
            output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(LCS_SRGB.toInt()).array())
            reader.copyTo(output, CS_TYPE_POSITION + 4, profile.start)
            writeZeros(output, profile.length)
            reader.copyTo(output, profile.start + profile.length, reader.size)
        }

        return StripReport.of(items, emptyList(), options, orientationWritten = false)
    }

    private fun findItems(reader: BinaryReader,
                          profile: BmpProfile?): List<MetadataItem> {
        if (profile == null) {
            return emptyList()
        }
        if (profile.isLinked) {
            return listOf(MetadataItem(MetadataCategory.People,
                                       MetadataLabel.LINKED_PROFILE_PATH,
                                       readPath(reader, profile),
                                       profile.length))
        }
        return listOf(MetadataItem(MetadataCategory.ColorProfile, MetadataLabel.ICC_PROFILE, byteSize = profile.length))
    }

    /*
     * Checks the headers and that the pixel data is all there, then returns the V5 profile if
     * there is one. The size check is what turns a truncated BMP into MalformedFileException
     * instead of a "clean" copy of half a picture.
     */
    private fun readProfile(reader: BinaryReader): BmpProfile? {
        reader.byteOrder = ByteOrder.LITTLE_ENDIAN
        reader.seek(0)
        if (reader.size < MIN_FILE_SIZE || reader.readAscii(2) != "BM") {
            throw MalformedFileException("BMP doesn't start with a BM signature")
        }
        reader.seek(PIXEL_OFFSET_POSITION)
        val pixelOffset = reader.readU32()
        val headerSize = reader.readU32()
        if (headerSize !in infoHeaderSizes) {
            throw MalformedFileException("BMP has an unknown info header size $headerSize")
        }
        reader.requireRemaining(headerSize - 4)
        if (pixelOffset < FILE_HEADER_SIZE + headerSize) {
            throw MalformedFileException("BMP pixel data starts inside its headers")
        }
        if (pixelOffset + pixelDataSize(reader, headerSize) > reader.size) {
            throw MalformedFileException("BMP pixel data is truncated")
        }

        if (headerSize < V5_HEADER_SIZE) {
            return null
        }
        reader.seek(CS_TYPE_POSITION)
        val csType = reader.readU32()
        if (csType != PROFILE_LINKED && csType != PROFILE_EMBEDDED) {
            return null
        }

        // The profile offset is relative to the info header, and must point past it.
        reader.seek(PROFILE_DATA_POSITION)
        val profileOffset = reader.readU32()
        val profileLength = reader.readU32()
        val profileStart = FILE_HEADER_SIZE + profileOffset
        if (profileOffset < headerSize || profileStart + profileLength > reader.size) {
            throw MalformedFileException("BMP color profile at $profileOffset (+$profileLength bytes) is outside the file")
        }
        return BmpProfile(csType == PROFILE_LINKED, profileStart, profileLength)
    }

    private fun pixelDataSize(reader: BinaryReader,
                              headerSize: Long): Long {
        val width: Long
        val height: Long
        val bitsPerPixel: Int
        var compression = BI_RGB
        var imageSize = 0L
        reader.seek(IMAGE_FIELDS_POSITION)
        if (headerSize == CORE_HEADER_SIZE) {
            width = reader.readU16().toLong()
            height = reader.readU16().toLong()
            reader.skip(PLANES_FIELD)
            bitsPerPixel = reader.readU16()
        } else {
            width = reader.readS32().toLong()
            // Negative height means rows are stored top-down.
            height = abs(reader.readS32().toLong())
            reader.skip(PLANES_FIELD)
            bitsPerPixel = reader.readU16()
            compression = reader.readU32()
            imageSize = reader.readU32()
        }

        if (compression !in uncompressedCompressions) {
            return imageSize
        }
        if (width <= 0 || bitsPerPixel == 0) {
            throw MalformedFileException("BMP has invalid dimensions or bit depth")
        }
        val rowSize = (bitsPerPixel * width + 31) / 32 * 4
        return rowSize * height
    }

    private fun readPath(reader: BinaryReader,
                         profile: BmpProfile): String {
        reader.seek(profile.start)
        val bytes = reader.readBytes(minOf(profile.length, MAX_PATH_LENGTH.toLong()).toInt())
        val terminator = bytes.indexOf(0).let { if (it == -1) bytes.size else it }
        return String(bytes, 0, terminator, Charsets.ISO_8859_1)
    }

    private fun writeZeros(output: OutputStream,
                           count: Long) {
        val zeros = ByteArray(minOf(count, ZERO_CHUNK_SIZE.toLong()).toInt())
        var left = count
        while (left > 0) {
            val chunk = minOf(left, zeros.size.toLong()).toInt()
            output.write(zeros, 0, chunk)
            left -= chunk
        }
    }

    // endregion Methods
}
