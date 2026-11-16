package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.HandlerHarness
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarning
import com.prateekmahendrakar.metadatawiper.metadata.MetadataWarningKind
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.ascii
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u16
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MovieSyntheticFileTest {

    // region State

    private val harness = HandlerHarness(IsoMediaHandler(FileFormat.Mp4))

    private val secret = "Jane".toByteArray()

    private val samples = listOf(listOf(ByteArray(64) { 7 }), listOf(ByteArray(64) { 9 }))

    private fun video(trakExtras: List<ByteArray> = emptyList(),
                      minfExtras: List<ByteArray> = emptyList()): MovieTestTrack {
        return MovieTestTrack("vide", "avc1", samples, trakExtras = trakExtras, minfExtras = minfExtras)
    }

    private fun track(handler: String,
                      format: String): MovieTestTrack = MovieTestTrack(handler, format, listOf(listOf(ByteArray(16) { 3 })))

    // endregion State

    // region Methods

    // region Boxes

    @Test
    fun freesMetadataBoxesAtEveryLevel() {
        val movie = MovieBuilder(listOf(video(trakExtras = listOf(box("udta", box("name", secret)), box("uuid", ByteArray(16) { 1 }, secret)),
                                              minfExtras = listOf(box("Jane", secret)))))
        movie.moovExtras = listOf(box("udta", box("©mod", u16(4), u16(0), secret)), box("uuid", ByteArray(16) { 1 }, secret))
        val input = movie.build().bytes

        val (output, report) = harness.strip(input)
        harness.assertHas(report.removed, MetadataCategory.Device, "Model", "Jane")
        harness.assertHas(report.removed, MetadataCategory.Other, "Title", "Jane")
        harness.assertHas(report.removed, MetadataCategory.Other, "Unknown box", "uuid")
        harness.assertHas(report.removed, MetadataCategory.Other, "Unknown box", "Jane")

        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf(secret))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
        assertArrayEquals(output, harness.strip(output).first)
    }

    @Test
    fun zeroesStalePaddingButKeepsTheBox() {
        val movie = MovieBuilder(listOf(video()))
        movie.moovExtras = listOf(box("free", secret))
        movie.topLevelExtras = listOf(box("skip", secret), box("mfra", ByteArray(8)))
        val input = movie.build().bytes

        val (output, report) = harness.strip(input)
        assertEquals(2, report.removed.count { it.label == "Leftover data" })
        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf(secret))
        assertEquals(listOf("ftyp", "moov", "mdat", "skip", "mfra"), topLevelTypes(output))
        assertEquals(emptyList<MetadataItem>(), harness.inspect(output).items)
    }

    @Test
    fun cleanFileIsCopiedUnchangedAndReportsNothing() {
        val input = MovieBuilder(listOf(video())).build().bytes
        assertEquals(emptyList<MetadataItem>(), harness.inspect(input).items)
        assertArrayEquals(input, harness.strip(input).first)
    }

    @Test
    fun readsDamagedUserDataAsUnknownAndStillFreesIt() {
        val movie = MovieBuilder(listOf(video()))
        movie.moovExtras = listOf(box("udta", u32(4096), ascii("©mod"), secret))
        val input = movie.build().bytes

        val (output, report) = harness.strip(input)
        harness.assertHas(report.removed, MetadataCategory.Other, "Unknown box", "udta")
        assertEquals(-1, output.indexOf(secret))
    }

    @Test
    fun acceptsQuickTimeUserDataTerminator() {
        val movie = MovieBuilder(listOf(video()))
        movie.moovExtras = listOf(box("udta", box("©mod", u16(4), u16(0), secret), u32(0)))
        harness.assertHas(harness.inspect(movie.build().bytes).items, MetadataCategory.Device, "Model", "Jane")
    }

    @Test
    fun hidesRedactedCoordinates() {
        val movie = MovieBuilder(listOf(video()))
        movie.moovExtras = listOf(box("udta", box("©xyz", u16(18), u16(0), ByteArray(18))))
        harness.assertHas(harness.inspect(movie.build().bytes).items, MetadataCategory.Location, "GPS", "")
    }

    @Test
    fun handlesLargeMediaDataAndCompactTables() {
        val camera = MovieBuilder(listOf(MovieTestTrack("vide", "avc1", samples, sizeBits = 16, largeOffsets = true)))
        camera.largeMediaData = true
        camera.moovExtras = listOf(box("udta", box("©mod", u16(4), u16(0), secret)))
        val built = camera.build()

        val output = harness.strip(built.bytes).first
        assertEquals(-1, output.indexOf(secret))
        for (span in built.chunkSpans.flatten()) {
            assertArrayEquals(built.bytes.copyOfRange(span.start.toInt(), span.end.toInt()), output.copyOfRange(span.start.toInt(), span.end.toInt()))
        }
    }

    // endregion Boxes

    // region End of file

    @Test
    fun cutsTrailersThatAreNotBoxes() {
        val movie = MovieBuilder(listOf(video()))
        movie.trailing = ByteArray(4) + byteArrayOf(1, 2, 3, 4) + secret
        val built = movie.build()
        val boxesEnd = built.bytes.size - movie.trailing.size

        val (output, report) = harness.strip(built.bytes)
        assertArrayEquals(built.bytes.copyOf(boxesEnd), output)
        assertTrue(report.removed.any { it.category == MetadataCategory.EmbeddedMedia && it.label == "Embedded data" && it.byteSize == 12L })
        assertArrayEquals(output, harness.strip(output).first)
    }

    @Test
    fun namesSamsungTrailers() {
        val movie = MovieBuilder(listOf(video()))
        movie.trailing = ByteArray(4) + ByteArray(4) { 1 } + "SEFHpayload".toByteArray() + "SEFT".toByteArray()
        val (_, report) = harness.strip(movie.build().bytes)
        assertTrue(report.removed.any { it.label == "Samsung trailer" })
    }

    @Test
    fun cutsRemovedBoxesAtTheEnd() {
        val movie = MovieBuilder(listOf(video()))
        movie.topLevelExtras = listOf(box("free", ByteArray(8)), box("uuid", ByteArray(16) { 2 }, secret))
        movie.trailing = ByteArray(5)
        val built = movie.build()
        val removedBoxes = movie.topLevelExtras.sumOf { it.size }
        val mediaEnd = built.bytes.size - movie.trailing.size - removedBoxes

        val (output, _) = harness.strip(built.bytes)
        assertArrayEquals(built.bytes.copyOf(mediaEnd), output)
    }

    @Test
    fun rejectsSamplesInsideATrailer() {
        val movie = MovieBuilder(listOf(video()))
        movie.trailing = ByteArray(512)
        movie.chunkOffsetShift = 128
        assertRejected(movie.build().bytes)
    }

    @Test
    fun rejectsSamplesInsideABoxThatWouldBeBlanked() {
        val movie = MovieBuilder(listOf(video()))
        movie.topLevelExtras = listOf(box("uuid", ByteArray(16) { 2 }, ByteArray(256)), box("mfra", ByteArray(8)))
        movie.chunkOffsetShift = 128
        assertRejected(movie.build().bytes)
    }

    @Test
    fun freesUnknownBoxesInFragments() {
        val movie = MovieBuilder(listOf(video()))
        val traf = box("traf", fullBox("tfhd", 0, 0, u32(1)), box("uuid", ByteArray(16) { 2 }, secret))
        movie.topLevelExtras = listOf(box("moof", fullBox("mfhd", 0, 0, u32(1)), traf), box("mfra", ByteArray(8)))
        val input = movie.build().bytes

        val output = harness.strip(input).first
        assertEquals(input.size, output.size)
        assertEquals(-1, output.indexOf(secret))
    }

    // endregion End of file

    // region Tracks

    @Test
    fun warnsAboutKeptTracks() {
        assertWarns(MovieBuilder(listOf(video(), track("sbtl", "tx3g"))), MetadataWarning(MetadataWarningKind.SubtitleTracksKept))
        assertWarns(MovieBuilder(listOf(video(), track("abcd", "abcd"))), MetadataWarning(MetadataWarningKind.UnknownTracksKept, listOf("abcd")))

        val chapters = listOf(box("tref", box("chap", u32(2))))
        val input = MovieBuilder(listOf(video(trakExtras = chapters), track("text", "text"))).build().bytes
        assertEquals(listOf(MetadataWarning(MetadataWarningKind.ChaptersKept)), harness.inspect(input).warnings)
    }

    @Test
    fun zeroesVersionOneTimestamps() {
        val time = ByteArray(8) { 0x11 }
        val header = fullBox("mvhd", 1, 0, time, time, u32(1000), ByteArray(8), ByteArray(80))
        val movie = MovieBuilder(listOf(video()))
        movie.moovExtras = listOf(header)
        val input = movie.build().bytes
        val output = harness.strip(input).first
        assertEquals(-1, output.indexOf(time))
    }

    // endregion Tracks

    // region Rejections

    @Test
    fun refusesCompressedMovieHeaders() {
        val movie = MovieBuilder(listOf(video()))
        movie.moovExtras = listOf(box("cmov", ByteArray(8)))
        assertUnsupported(movie.build().bytes, UnsupportedReason.NotYetSupported)
    }

    @Test
    fun refusesEncryptedFiles() {
        assertUnsupported(MovieBuilder(listOf(track("vide", "encv"))).build().bytes, UnsupportedReason.Encrypted)
        assertUnsupported(MovieBuilder(listOf(track("soun", "enca"))).build().bytes, UnsupportedReason.Encrypted)

        val protectionHeader = MovieBuilder(listOf(video()))
        protectionHeader.moovExtras = listOf(fullBox("pssh", 0, 0, ByteArray(16), u32(0)))
        assertUnsupported(protectionHeader.build().bytes, UnsupportedReason.Encrypted)
    }

    @Test
    fun refusesSamplesInAnotherFile() {
        val external = box("dinf", fullBox("dref", 0, 0, u32(1), fullBox("url ", 0, 0, "file:///a".toByteArray())))
        assertUnsupported(MovieBuilder(listOf(video(minfExtras = listOf(external)))).build().bytes, UnsupportedReason.ExternalReferences)

        val selfContained = box("dinf", fullBox("dref", 0, 0, u32(1), fullBox("url ", 0, 1)))
        harness.inspect(MovieBuilder(listOf(video(minfExtras = listOf(selfContained)))).build().bytes)
    }

    @Test
    fun rejectsFilesWithoutExactlyOneMovie() {
        val movie = MovieBuilder(listOf(video())).build().bytes
        assertRejected(concat(box("ftyp", ascii("isom"), u32(0)), box("mdat", ByteArray(8))))
        assertRejected(movie + box("moov", ByteArray(0)))
    }

    @Test
    fun rejectsTracksMissingTheirStructure() {
        assertRejected(concat(box("ftyp", ascii("isom"), u32(0)), box("moov", box("trak", ByteArray(0)))))
    }

    // endregion Rejections

    private fun topLevelTypes(bytes: ByteArray): List<String> {
        return BoxReader(BinaryReader(ByteArrayChannel(bytes))).readTopLevel().boxes.map { it.type }
    }

    private fun assertWarns(movie: MovieBuilder,
                            warning: MetadataWarning) {
        assertEquals(listOf(warning), harness.inspect(movie.build().bytes).warnings)
    }

    private fun assertRejected(input: ByteArray) {
        assertThrows(MalformedFileException::class.java) { harness.inspect(input) }
        assertWritesNothing<MalformedFileException>(input)
    }

    private fun assertUnsupported(input: ByteArray,
                                  reason: UnsupportedReason) {
        val failure = assertThrows(UnsupportedFileException::class.java) { harness.inspect(input) }
        assertEquals(reason, failure.reason)
        assertWritesNothing<UnsupportedFileException>(input)
    }

    private inline fun <reified T : Throwable> assertWritesNothing(input: ByteArray) {
        val output = ByteArrayOutputStream()
        assertThrows(T::class.java) { IsoMediaHandler(FileFormat.Mp4).strip(ByteArrayChannel(input), output, StripOptions()) }
        assertEquals(0, output.size())
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        return (0..size - pattern.size).firstOrNull { start -> pattern.indices.all { this[start + it] == pattern[it] } } ?: -1
    }

    // endregion Methods
}
