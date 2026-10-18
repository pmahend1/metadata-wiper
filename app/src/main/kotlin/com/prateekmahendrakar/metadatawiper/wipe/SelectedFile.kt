package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions

// A picked file, inspected once at selection so composables never touch the file.
data class SelectedFile(val uri: Uri,
                        val displayName: String,
                        val format: FileFormat,
                        val inspection: InspectionResult?,
                        val metadataGroups: List<MetadataGroup>) {

    // region State

    // No inspection means the format has no handler yet or the file couldn't be parsed.
    val canBeCleaned: Boolean
        get() = inspection != null

    // endregion State

    // region Methods

    fun hasRemovableMetadata(options: StripOptions): Boolean {
        return inspection?.items?.any { options.keeps(it) == false } == true
    }

    // endregion Methods
}
