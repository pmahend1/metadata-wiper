package com.prateekmahendrakar.metadatawiper.metadata

// A code rather than a sentence, so the app can show it in the user's language. types fills in the sentence: the unrecognised boxes or items, or a size.
data class MetadataWarning(val kind: MetadataWarningKind,
                           val types: List<String> = emptyList())
