package com.prateekmahendrakar.metadatawiper.wipe

import android.net.Uri
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.metadata.InspectionResult
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.StripOptions

// A picked file, inspected once at selection so composables never touch the file.
data class SelectedFile(val uri: Uri,
                        val displayName: String,
                        val format: FileFormat,
                        val byteSize: Long,
                        val inspection: InspectionResult?,
                        val metadataGroups: List<MetadataGroup>) {

    // region State

    // No inspection means the format has no handler yet or the file couldn't be parsed.
    val canBeCleaned: Boolean
        get() = inspection != null

    // endregion State

    // region Methods

    fun hasRemovableMetadata(options: StripOptions): Boolean = removableCategories(options).isNotEmpty()

    fun removableCategories(options: StripOptions): Set<MetadataCategory> {
        return inspection?.items?.filter { options.keeps(it) == false }
                                ?.map { it.category }
                                ?.toSet()
               ?: emptySet()
    }

    // endregion Methods
}
