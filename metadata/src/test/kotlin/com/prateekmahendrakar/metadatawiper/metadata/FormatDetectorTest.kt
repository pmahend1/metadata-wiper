package com.prateekmahendrakar.metadatawiper.metadata

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class FormatDetectorTest {

    @Test
    fun detectsEachSupportedSignature() {
        val cases = mapOf(bytes(0xFF, 0xD8, 0xFF, 0xE0) to FileFormat.Jpeg,
                          bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) to FileFormat.Png,
                          ascii("GIF87a") to FileFormat.Gif,
                          ascii("GIF89a") to FileFormat.Gif,
                          ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBPVP8 ") to FileFormat.WebP,
                          ftyp("heic", "mif1", "heic") to FileFormat.Heif,
                          ftyp("mif1", "mif1", "miaf") to FileFormat.Heif,
                          ftyp("avif", "mif1", "avif") to FileFormat.Avif,
                          ftyp("mif1", "mif1", "avif") to FileFormat.Avif,
                          ftyp("avis", "avis", "msf1") to FileFormat.Avif,
                          bmp(infoHeaderSize = 40) to FileFormat.Bmp,
                          bmp(infoHeaderSize = 124) to FileFormat.Bmp,
                          bytes(0x49, 0x49, 0x2A, 0x00) to FileFormat.Tiff,
                          bytes(0x4D, 0x4D, 0x00, 0x2A) to FileFormat.Tiff)

        for ((header, expected) in cases) {
            assertEquals(expected, FormatDetector.detect(header))
        }
    }

    @Test
    fun reportsUnknownForOtherFiles() {
        val cases = listOf(ByteArray(0),
                           bytes(0xFF, 0xD8),
                           ascii("GIF8"),
                           ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WAVEfmt "),
                           ftyp("isom", "isom", "mp41"),
                           ftyp("qt  ", "qt  "),
                           ascii("%PDF-1.7"),
                           bmp(infoHeaderSize = 7),
                           ascii("BM"))

        for (header in cases) {
            assertEquals(FileFormat.Unknown, FormatDetector.detect(header))
        }
    }

    @Test
    fun neverThrowsOnRandomHeaders() {
        val random = Random(42)
        repeat(10_000) {
            FormatDetector.detect(random.nextBytes(random.nextInt(0, FormatDetector.HEADER_SIZE + 1)))
        }
    }

    // A brand list that claims to be longer than the header must not read past the end.
    @Test
    fun handlesFtypBoxLargerThanHeader() {
        val header = ftyp("heic", "mif1").also { it[3] = 0x7F }
        assertEquals(FileFormat.Heif, FormatDetector.detect(header))
    }

    private fun ftyp(majorBrand: String,
                     vararg compatibleBrands: String): ByteArray {
        val size = 16 + compatibleBrands.size * 4
        val body = ascii("ftyp") + ascii(majorBrand) + ByteArray(4) + compatibleBrands.fold(ByteArray(0)) { all, brand -> all + ascii(brand) }
        return bytes(0, 0, 0, size) + body
    }

    private fun bmp(infoHeaderSize: Int): ByteArray = ascii("BM") + ByteArray(12) + bytes(infoHeaderSize, 0, 0, 0)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)
}
