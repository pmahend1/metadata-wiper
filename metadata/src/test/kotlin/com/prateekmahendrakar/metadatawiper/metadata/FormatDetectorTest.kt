package com.prateekmahendrakar.metadatawiper.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
                          bytes(0x4D, 0x4D, 0x00, 0x2A) to FileFormat.Tiff,
                          ftyp("isom", "isom", "iso2", "avc1", "mp41") to FileFormat.Mp4,
                          ftyp("mp42", "mp42", "isom") to FileFormat.Mp4,
                          ftyp("M4V ", "M4V ", "M4A ", "mp42") to FileFormat.Mp4,
                          ftyp("XAVC", "XAVC", "mp42") to FileFormat.Mp4,
                          ftyp("abcd", "abcd", "isom") to FileFormat.Mp4,
                          ftyp("qt  ", "qt  ") to FileFormat.QuickTime,
                          ftyp("abcd", "qt  ") to FileFormat.QuickTime,
                          ftyp("3gp4", "isom", "3gp4") to FileFormat.ThreeGpp,
                          ftyp("3g2a", "3g2a") to FileFormat.ThreeGpp,
                          box(0x20, "moov") to FileFormat.QuickTime,
                          box(0x08, "wide") to FileFormat.QuickTime,
                          box(0x01, "mdat") to FileFormat.QuickTime,
                          ebml("matroska") to FileFormat.Matroska,
                          ebml("webm") to FileFormat.WebM,
                          ebml("webm", padding = 2) to FileFormat.WebM,
                          ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("AVI LIST") to FileFormat.Avi,
                          bytes(0x00, 0x00, 0x01, 0xBA, 0x44) to FileFormat.MpegPs,
                          transportStream(packetSize = 188, syncOffset = 0) to FileFormat.MpegTs,
                          transportStream(packetSize = 192, syncOffset = 4) to FileFormat.MpegTs)

        for ((header, expected) in cases) {
            assertEquals(expected, FormatDetector.detect(header))
        }
    }

    // A HEIC with an image sequence still lists video brands, but it's an image first.
    @Test
    fun prefersImageBrandsOverVideoBrands() {
        assertEquals(FileFormat.Heif, FormatDetector.detect(ftyp("msf1", "msf1", "isom", "mp41")))
        assertEquals(FileFormat.Avif, FormatDetector.detect(ftyp("avis", "avis", "iso8", "mp41")))
    }

    @Test
    fun reportsUnknownForOtherFiles() {
        val cases = listOf(ByteArray(0),
                           bytes(0xFF, 0xD8),
                           ascii("GIF8"),
                           ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WAVEfmt "),
                           ascii("%PDF-1.7"),
                           bmp(infoHeaderSize = 7),
                           ascii("BM"),
                           // Audio waits for Phase 3; CR3 raw photos share the container.
                           ftyp("M4A ", "M4A ", "mp42", "isom"),
                           ftyp("crx ", "crx ", "isom"),
                           ftyp("abcd", "efgh"),
                           box(0x04, "moov"),
                           box(0x20, "trak"),
                           ebml("mkv3"),
                           ebmlWithoutDocType(),
                           bytes(0x1A, 0x45, 0xDF, 0xA3),
                           // A lone 0x47 is just a 'G', not a transport stream.
                           ascii("Gx") + ByteArray(400),
                           transportStream(packetSize = 188, syncOffset = 0).copyOf(300))

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

    @Test
    fun detectsEveryFixture() {
        val formatsByPrefix = mapOf("jpeg-" to FileFormat.Jpeg,
                                    "png-" to FileFormat.Png,
                                    "webp-" to FileFormat.WebP,
                                    "heif-" to FileFormat.Heif,
                                    "avif-" to FileFormat.Avif,
                                    "gif-" to FileFormat.Gif,
                                    "bmp-" to FileFormat.Bmp,
                                    "mp4-" to FileFormat.Mp4,
                                    "m4v-" to FileFormat.Mp4,
                                    "mov-" to FileFormat.QuickTime,
                                    "3gp-" to FileFormat.ThreeGpp,
                                    "mkv-" to FileFormat.Matroska,
                                    "webm-" to FileFormat.WebM,
                                    "avi-" to FileFormat.Avi,
                                    "ts-" to FileFormat.MpegTs,
                                    "m2ts-" to FileFormat.MpegTs)
        val fixtures = File(javaClass.getResource("/fixtures")!!.toURI()).listFiles()!!.sortedBy { it.name }
        assertTrue(fixtures.isNotEmpty())

        for (fixture in fixtures) {
            val expected = formatsByPrefix.entries.single { fixture.name.startsWith(it.key) }.value
            val header = fixture.inputStream().use { it.readNBytes(FormatDetector.HEADER_SIZE) }
            assertEquals(fixture.name, expected, FormatDetector.detect(header))
        }
    }

    private fun ftyp(majorBrand: String,
                     vararg compatibleBrands: String): ByteArray {
        val size = 16 + compatibleBrands.size * 4
        val body = ascii("ftyp") + ascii(majorBrand) + ByteArray(4) + compatibleBrands.fold(ByteArray(0)) { all, brand -> all + ascii(brand) }
        return bytes(0, 0, 0, size) + body
    }

    private fun box(size: Int,
                    type: String): ByteArray = bytes(0, 0, 0, size) + ascii(type) + ByteArray(8)

    // EBMLVersion, then DocType, as mkvmerge and browsers write the header.
    private fun ebml(docType: String,
                     padding: Int = 0): ByteArray {
        val docTypeBytes = ascii(docType) + ByteArray(padding)
        val children = bytes(0x42, 0x86, 0x81, 0x01) + bytes(0x42, 0x82, 0x80 or docTypeBytes.size) + docTypeBytes
        return bytes(0x1A, 0x45, 0xDF, 0xA3, 0x80 or children.size) + children + bytes(0x18, 0x53, 0x80, 0x67)
    }

    private fun ebmlWithoutDocType(): ByteArray {
        val children = bytes(0x42, 0x86, 0x81, 0x01)
        return bytes(0x1A, 0x45, 0xDF, 0xA3, 0x80 or children.size) + children
    }

    private fun transportStream(packetSize: Int,
                                syncOffset: Int): ByteArray {
        val bytes = ByteArray(packetSize * 3)
        for (packet in 0 until 3) {
            bytes[syncOffset + packet * packetSize] = 0x47
        }
        return bytes
    }

    private fun bmp(infoHeaderSize: Int): ByteArray = ascii("BM") + ByteArray(12) + bytes(infoHeaderSize, 0, 0, 0)

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)
}
