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
 * MPEG transport and program streams (.ts, .m2ts, .mts, .mpg) have no container metadata to
 * strip, but camcorders write the recording date, time and GPS into the video stream itself
 * (H.264 SEI `MDPM`, MPEG-2 `user_data`). Without parsing that stream a file can't be shown to
 * be clean, so both calls refuse it with the reason rather than report "nothing found".
 */
class MpegStreamHandler(override val format: FileFormat) : FormatHandler {

    init {
        require(format in setOf(FileFormat.MpegTs, FileFormat.MpegPs)) { "MpegStreamHandler doesn't handle $format" }
    }

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
