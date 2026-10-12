package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult

// A picked file, inspected once at selection so composables never touch the file.
data class SelectedFile(val uri: Uri,
                        val displayName: String,
                        val format: FileFormat,
                        val inspection: InspectionResult?,
                        val exifDetails: Map<String, String>,
                        val hasRemovableMetadata: Boolean) {

    // region State

    // No inspection means the format has no handler yet or the file couldn't be parsed.
    val canBeCleaned: Boolean
        get() = inspection != null

    // endregion State
}
