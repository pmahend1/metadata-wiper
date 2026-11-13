package com.prateekmahendrakar.metadatawiper.verify

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prateekmahendrakar.metadatawiper.VideoFixtures
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.matroska.MatroskaHandler
import com.prateekmahendrakar.metadatawiper.metadata.movie.IsoMediaHandler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.channels.FileChannel

@RunWith(AndroidJUnit4::class)
class VideoDecodeVerifierTest {

    // region State

    private val verifier = VideoDecodeVerifier()
    private val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    // endregion State

    // region Methods

    @Test
    fun passesStrippedFixtures() {
        assertEquals(DecodeCheck.Passed, verifyStripped("mp4-android.mp4", FileFormat.Mp4))
        assertEquals(DecodeCheck.Passed, verifyStripped("mp4-rotated.mp4", FileFormat.Mp4))
        assertEquals(DecodeCheck.Passed, verifyStripped("mp4-fragmented.mp4", FileFormat.Mp4))
        assertEquals(DecodeCheck.Passed, verifyStripped("mov-apple.mov", FileFormat.QuickTime))
        assertEquals(DecodeCheck.Passed, verifyStripped("3gp-metadata.3gp", FileFormat.ThreeGpp))
        assertEquals(DecodeCheck.Passed, verifyStripped("mkv-tags.mkv", MatroskaHandler(FileFormat.Matroska)))
        assertEquals(DecodeCheck.Passed, verifyStripped("webm-browser.webm", MatroskaHandler(FileFormat.WebM)))
    }

    @Test
    fun failsATruncatedCopy() {
        assertEquals(DecodeCheck.Failed, verifyDamaged { it.writeBytes(it.readBytes().copyOf(it.length().toInt() / 2)) })
    }

    @Test
    fun failsACopyWithItsHeaderZeroed() {
        assertEquals(DecodeCheck.Failed, verifyDamaged { it.writeBytes(ByteArray(it.length().toInt())) })
    }

    @Test
    fun skipsWhenThePlatformCannotDemuxTheOriginal() {
        val original = File.createTempFile("original", ".bin", cacheDir)
        val cleaned = File.createTempFile("cleaned", ".bin", cacheDir)
        try {
            original.writeBytes(ByteArray(1024) { it.toByte() })
            cleaned.writeBytes(ByteArray(1024) { it.toByte() })
            assertEquals(DecodeCheck.Skipped, FileChannel.open(original.toPath()).use { verifier.verify(it, cleaned) })
        } finally {
            original.delete()
            cleaned.delete()
        }
    }

    private fun verifyStripped(name: String,
                               format: FileFormat): DecodeCheck = verifyStripped(name, IsoMediaHandler(format))

    private fun verifyStripped(name: String,
                               handler: FormatHandler): DecodeCheck = strip(name, handler) { _, check -> check() }

    private fun verifyDamaged(damage: (File) -> Unit): DecodeCheck {
        return strip("mp4-android.mp4", IsoMediaHandler(FileFormat.Mp4)) { cleaned, check ->
            damage(cleaned)
            check()
        }
    }

    private fun strip(name: String,
                      handler: FormatHandler,
                      block: (File, () -> DecodeCheck) -> DecodeCheck): DecodeCheck {
        val original = VideoFixtures.copyToCache(name)
        val cleaned = File.createTempFile("cleaned", name, cacheDir)
        try {
            return FileChannel.open(original.toPath()).use { channel ->
                cleaned.outputStream().use { handler.strip(channel, it, StripOptions()) }
                block(cleaned) { verifier.verify(channel, cleaned) }
            }
        } finally {
            original.delete()
            cleaned.delete()
        }
    }

    // endregion Methods
}
