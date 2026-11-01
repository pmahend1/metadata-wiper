package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prateekmahendrakar.metadatawiper.VideoFixtures
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandlerRegistry
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.movie.IsoMediaHandler
import com.prateekmahendrakar.metadatawiper.storage.DocumentStore
import com.prateekmahendrakar.metadatawiper.storage.TempFileStore
import com.prateekmahendrakar.metadatawiper.verify.DecodeVerifier
import com.prateekmahendrakar.metadatawiper.verify.VideoDecodeVerifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WipeFilesUseCaseTest {

    // region State

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val tempDirectory = File(context.cacheDir, "test-wipe")
    private val workDirectory = File(context.cacheDir, "test-work")
    private val tempFiles
        get() = File(tempDirectory, "wipe").listFiles().orEmpty()

    // endregion State

    // region Methods

    @Before
    fun setUp() {
        tempDirectory.mkdirs()
        workDirectory.mkdirs()
    }

    @After
    fun tearDown() {
        tempDirectory.deleteRecursively()
        workDirectory.deleteRecursively()
    }

    @Test
    fun cleansAVideoAndReportsEveryPhase() {
        val phases = mutableListOf<WipePhase>()
        val output = File(workDirectory, "clean.mp4")
        val result = wipe(useCase(), selected("mp4-android.mp4"), LocalFileDestination(output), WipeMonitor(onProgress = { phase, _ -> phases += phase }))

        assertTrue("$result", result is CleanedResult)
        assertEquals(false, (result as CleanedResult).decodeCheckSkipped)
        assertTrue(output.length() > 0)
        assertEquals(listOf(WipePhase.Stripping, WipePhase.Verifying), phases.distinct())
        assertTrue(tempFiles.isEmpty())
    }

    @Test
    fun cancellingMidStripLeavesNoFiles() {
        // The first check is the one before the strip starts, so the second is the stream's first write.
        var checks = 0
        val output = File(workDirectory, "cancelled.mp4")
        val monitor = WipeMonitor(isCancelled = { ++checks > 1 })
        val result = wipe(useCase(), selected("mp4-android.mp4"), LocalFileDestination(output), monitor)

        assertEquals(FailedResult(WipeFailure.Cancelled), result)
        assertFalse(output.exists())
        assertTrue(tempFiles.isEmpty())
    }

    @Test
    fun cancellingBeforeStartingDoesNoWork() {
        val output = File(workDirectory, "cancelled.mp4")
        val result = wipe(useCase(), selected("mp4-android.mp4"), LocalFileDestination(output), WipeMonitor(isCancelled = { true }))

        assertEquals(FailedResult(WipeFailure.Cancelled), result)
        assertFalse(output.exists())
    }

    @Test
    fun lowSpaceFailsBeforeAnyWorkWithTheSizeNeeded() {
        val file = selected("mp4-android.mp4")
        val output = File(workDirectory, "clean.mp4")
        val result = wipe(useCase(freeSpace = 1L), file, LocalFileDestination(output), WipeMonitor())

        assertEquals(FailedResult(WipeFailure.InsufficientSpace, WipeSpace.neededFor(file.byteSize)), result)
        assertFalse(output.exists())
        assertTrue(tempFiles.isEmpty())
    }

    @Test
    fun cancelCannotInterruptAnOverwrite() {
        val fixture = VideoFixtures.copyToCache("mp4-android.mp4")
        try {
            var isCancelled = false
            val monitor = WipeMonitor(isCancelled = { isCancelled }, onProgress = { phase, _ -> isCancelled = phase == WipePhase.Writing })
            val useCase = useCase()
            val result = useCase.wipe(useCase.inspect(Uri.fromFile(fixture)), OverwriteDestination, StripOptions(), monitor)

            assertTrue("$result", result is CleanedResult)
            assertEquals(false, useCase.inspect(Uri.fromFile(fixture)).hasRemovableMetadata(StripOptions()))
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun aFileWithoutAHandlerSaysWhy() {
        val text = File(workDirectory, "notes.txt").apply { writeText("not a video") }
        val file = useCase().inspect(Uri.fromFile(text))

        assertEquals(FileFormat.Unknown, file.format)
        assertEquals(UnsupportedReason.UnknownFormat, file.unsupportedReason)
    }

    private fun selected(name: String): SelectedFile {
        val file = VideoFixtures.copyToCache(name).copyTo(File(workDirectory, name), overwrite = true)
        return useCase().inspect(Uri.fromFile(file))
    }

    private fun useCase(freeSpace: Long = Long.MAX_VALUE): WipeFilesUseCase {
        return WipeFilesUseCase(DocumentStore(context.contentResolver),
                                TempFileStore(tempDirectory) { freeSpace },
                                DecodeVerifier(),
                                VideoDecodeVerifier(),
                                FormatHandlerRegistry(listOf(IsoMediaHandler(FileFormat.Mp4))))
    }

    private fun wipe(useCase: WipeFilesUseCase,
                     file: SelectedFile,
                     destination: WipeDestination,
                     monitor: WipeMonitor): WipeResult = useCase.wipe(file, destination, StripOptions(), monitor)

    // endregion Methods
}
