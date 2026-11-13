package com.prateekmahendrakar.metadatawiper.metadata.avi

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
 * Strips AVI, including OpenDML files continued in `RIFF AVIX` chunks. Metadata chunks become
 * `JUNK` chunks of the same size, so `idx1` and OpenDML index offsets stay valid and media data
 * is copied byte for byte. See AviStripPlanner for what is blanked and kept.
 */
class AviHandler : FormatHandler {

    // region State

    override val format = FileFormat.Avi

    // endregion State

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val plan = AviStripPlanner(BinaryReader(input)).plan()
        return InspectionResult(format, plan.items, plan.warnings)
    }

    // The whole file is planned before the first byte is written, so a rejected file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val plan = AviStripPlanner(reader).plan()
        BlankedCopy.write(reader, output, plan.blanked, plan.outputLength)

        // AVI has no orientation tag, so no orientation item is ever reported or written.
        return StripReport.of(plan.items, plan.warnings, options, orientationWritten = false)
    }

    // endregion Methods
}
