package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.FormatHandler
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions
import com.prateekmahendrakar.metadatawiper.metadata.StripReport
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/*
 * Strips HEIF/HEIC and AVIF, which share the same container. The output differs from the input
 * only in the blanked metadata bytes, so no `iloc` offset has to move, except that removed boxes
 * at the end can be cut off. See HeifStripPlanner for what is blanked, cut and kept.
 */
class HeifHandler(override val format: FileFormat) : FormatHandler {

    init {
        require(format == FileFormat.Heif || format == FileFormat.Avif) { "HeifHandler handles HEIF and AVIF, not $format" }
    }

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val plan = HeifStripPlanner(BinaryReader(input), StripOptions()).plan()
        return InspectionResult(format, plan.items, plan.warnings)
    }

    // The whole file is planned before the first byte is written, so a malformed file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val plan = HeifStripPlanner(reader, options).plan()

        var position = 0L
        for (range in plan.blanked) {
            reader.copyTo(output, position, range.span.start)
            range.writeTo(output)
            position = range.span.end
        }
        reader.copyTo(output, position, plan.outputLength)

        // Orientation lives in the kept `irot`/`imir` properties, so no EXIF orientation is ever written.
        return StripReport.of(plan.items, plan.warnings, options, orientationWritten = false)
    }

    // endregion Methods
}
