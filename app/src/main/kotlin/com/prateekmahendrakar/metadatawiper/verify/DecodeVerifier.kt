package com.prateekmahendrakar.metadatawiper.verify

import android.graphics.BitmapFactory
import android.os.Build
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import java.io.File
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel

// Proves a stripped file still decodes to an image of the original's dimensions.
class DecodeVerifier {

    // region State

    private companion object {
        // A heavily subsampled decode still walks the whole entropy-coded stream, at a fraction of the memory.
        const val SAMPLE_SIZE = 8
    }

    // endregion State

    // region Methods

    fun verify(format: FileFormat,
               original: SeekableByteChannel,
               cleaned: File): DecodeCheck {
        if (canDecode(format) == false) {
            return DecodeCheck.Skipped
        }

        // Not closed: closing a channel-backed stream would close the caller's channel.
        original.position(0)
        val originalBounds = bounds(Channels.newInputStream(original))
        if (originalBounds == null) {
            return DecodeCheck.Skipped
        }

        val cleanedBounds = cleaned.inputStream().use { bounds(it) }
        if (cleanedBounds != originalBounds) {
            return DecodeCheck.Failed
        }

        val options = BitmapFactory.Options().apply { inSampleSize = SAMPLE_SIZE }
        val bitmap = BitmapFactory.decodeFile(cleaned.path, options) ?: return DecodeCheck.Failed
        bitmap.recycle()
        return DecodeCheck.Passed
    }

    private fun canDecode(format: FileFormat): Boolean {
        return when (format) {
            FileFormat.Jpeg, FileFormat.Png, FileFormat.WebP, FileFormat.Gif, FileFormat.Bmp -> true
            FileFormat.Heif -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            FileFormat.Avif -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            FileFormat.Tiff, FileFormat.Unknown -> false
        }
    }

    private fun bounds(input: InputStream): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }
        return options.outWidth to options.outHeight
    }

    // endregion Methods
}
