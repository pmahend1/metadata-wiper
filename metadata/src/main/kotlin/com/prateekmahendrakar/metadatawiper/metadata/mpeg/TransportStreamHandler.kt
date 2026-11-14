package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.io.BlankedCopy
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/*
 * Strips MPEG transport streams (.ts, .m2ts, .mts). Only SEI user data inside the video stream
 * is overwritten, in place, so every packet stays where it was and the output is the same size.
 * See TransportStreamPlanner for what is read and kept.
 */
class TransportStreamHandler : FormatHandler {

    // region State

    override val format = FileFormat.MpegTs

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val plan = TransportStreamPlanner(BinaryReader(input)).plan()
        return InspectionResult(format, plan.items, plan.warnings)
    }

    // The whole file is planned before the first byte is written, so a rejected file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val plan = TransportStreamPlanner(reader).plan()
        BlankedCopy.write(reader, output, plan.blanked)

        // Transport streams have no orientation tag, so no orientation item is ever reported or written.
        return StripReport.of(plan.items, plan.warnings, options, orientationWritten = false)
    }

    // endregion Methods
}
