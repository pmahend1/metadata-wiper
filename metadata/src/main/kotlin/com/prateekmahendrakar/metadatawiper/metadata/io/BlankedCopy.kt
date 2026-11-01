package com.prateekmahendrakar.metadatawiper.metadata.io

import java.io.OutputStream

/*
 * The write half of every size-preserving strip: copy the input to [output], replacing each
 * blanked range, and stop at [outputLength]. Ranges must be sorted and must not overlap; one
 * that starts at or past [outputLength] is dropped with the rest of the file.
 */
object BlankedCopy {

    // region Methods

    fun write(reader: BinaryReader,
              output: OutputStream,
              blanked: List<BlankedRange>,
              outputLength: Long = reader.size) {
        require(outputLength in 0..reader.size) { "Output length $outputLength is outside the file (${reader.size} bytes)" }

        var position = 0L
        for (range in blanked) {
            require(range.span.start >= position) { "Blanked ranges must be sorted and must not overlap" }
            if (range.span.start >= outputLength) {
                break
            }
            require(range.span.end <= outputLength) { "A blanked range can't straddle the end of the output" }
            reader.copyTo(output, position, range.span.start)
            range.writeTo(output)
            position = range.span.end
        }
        reader.copyTo(output, position, outputLength)
    }

    // endregion Methods
}
