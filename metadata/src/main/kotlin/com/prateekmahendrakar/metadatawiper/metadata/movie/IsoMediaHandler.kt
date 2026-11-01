package com.prateekmahendrakar.metadatawiper.metadata.movie

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
 * Strips MP4, MOV, 3GP and M4V, which share one box structure. Media data is copied byte for
 * byte and nothing moves, so no `stco` offset changes, except that a trailer and removed boxes
 * at the end can be cut off. See MovieStripPlanner for what is blanked, cut and kept.
 */
class IsoMediaHandler(override val format: FileFormat) : FormatHandler {

    init {
        require(format in setOf(FileFormat.Mp4, FileFormat.QuickTime, FileFormat.ThreeGpp)) { "IsoMediaHandler doesn't handle $format" }
    }

    // region Methods

    override fun inspect(input: SeekableByteChannel): InspectionResult {
        val plan = MovieStripPlanner(BinaryReader(input)).plan()
        return InspectionResult(format, plan.items, plan.warnings)
    }

    // The whole file is planned before the first byte is written, so a rejected file produces no output.
    override fun strip(input: SeekableByteChannel,
                       output: OutputStream,
                       options: StripOptions): StripReport {
        val reader = BinaryReader(input)
        val plan = MovieStripPlanner(reader).plan()
        BlankedCopy.write(reader, output, plan.blanked, plan.outputLength)

        // Rotation lives in the kept `tkhd` matrix, so no orientation item is ever reported or written.
        return StripReport.of(plan.items, plan.warnings, options, orientationWritten = false)
    }

    // endregion Methods
}
