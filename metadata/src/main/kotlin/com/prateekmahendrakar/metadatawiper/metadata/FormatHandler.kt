package com.prateekmahendrakar.metadatawiper.metadata

import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

interface FormatHandler {
    val format: FileFormat

    fun inspect(input: SeekableByteChannel): InspectionResult

    fun strip(
        input: SeekableByteChannel,
        output: OutputStream,
        options: StripOptions
    ): StripReport
}