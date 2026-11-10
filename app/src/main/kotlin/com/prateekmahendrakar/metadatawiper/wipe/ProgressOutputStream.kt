package com.prateekmahendrakar.metadatawiper.wipe

import java.io.OutputStream
import kotlin.coroutines.cancellation.CancellationException

/*
 * Counts bytes against [totalBytes] and stops the copy once [isCancelled] turns true, which is
 * how a wipe is cancelled without handlers knowing about it. Progress is reported once per
 * percent, so a multi-gigabyte copy doesn't flood the UI with updates.
 */
class ProgressOutputStream(private val output: OutputStream,
                           private val totalBytes: Long,
                           private val isCancelled: () -> Boolean,
                           private val onProgress: (Float) -> Unit) : OutputStream() {

    // region State

    private var writtenBytes = 0L
    private var reportedPercent = 0L

    // endregion State

    // region Methods

    override fun write(byte: Int) {
        throwIfCancelled()
        output.write(byte)
        advance(1)
    }

    override fun write(bytes: ByteArray,
                       offset: Int,
                       length: Int) {
        throwIfCancelled()
        output.write(bytes, offset, length)
        advance(length)
    }

    override fun flush() = output.flush()

    override fun close() = output.close()

    private fun throwIfCancelled() {
        if (isCancelled()) {
            throw CancellationException("Wipe cancelled")
        }
    }

    private fun advance(count: Int) {
        writtenBytes += count
        if (totalBytes <= 0) {
            return
        }
        val percent = minOf(writtenBytes, totalBytes) * 100 / totalBytes
        if (percent != reportedPercent) {
            reportedPercent = percent
            onProgress(percent / 100f)
        }
    }

    // endregion Methods
}
