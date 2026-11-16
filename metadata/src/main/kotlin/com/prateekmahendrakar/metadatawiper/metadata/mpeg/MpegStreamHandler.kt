package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/*
 * MPEG program streams (.mpg, .vob) have no container metadata to strip, but camcorders write
 * the recording date and time into the MPEG-2 video stream's `user_data`, which isn't parsed.
 * Without that a file can't be shown to be clean, so both calls refuse it with the reason
 * rather than report "nothing found".
 */
class MpegStreamHandler : FormatHandler {

    // region State

    override val format = FileFormat.MpegPs

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult = throw unsupported()

    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport = throw unsupported()

    private fun unsupported(): UnsupportedFileException {
        return UnsupportedFileException(UnsupportedReason.InStreamMetadata, "$format can hold metadata inside the video stream")
    }

    // endregion Methods
}
