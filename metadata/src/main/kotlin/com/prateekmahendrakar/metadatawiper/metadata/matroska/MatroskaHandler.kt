package com.prateekmahendrakar.metadatawiper.metadata.matroska

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
 * Strips MKV and WebM, which share one element structure. Metadata elements become Void
 * elements of the same size, so Cues and SeekHead positions stay valid and media data is copied
 * byte for byte. See MatroskaStripPlanner for what is voided, zeroed and kept.
 */
class MatroskaHandler(override val format: FileFormat) : FormatHandler {

    init {
        require(format in setOf(FileFormat.Matroska, FileFormat.WebM)) { "MatroskaHandler doesn't handle $format" }
    }

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val plan = MatroskaStripPlanner(BinaryReader(input)).plan()
        return InspectionResult(format, plan.items, plan.warnings)
    }

    // The whole file is planned before the first byte is written, so a rejected file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val plan = MatroskaStripPlanner(reader).plan()
        BlankedCopy.write(reader, output, plan.blanked, plan.outputLength)

        // Rotation and colour live in the kept track entries, so no orientation item is ever reported or written.
        return StripReport.of(plan.items, plan.warnings, options, orientationWritten = false)
    }

    // endregion Methods
}
