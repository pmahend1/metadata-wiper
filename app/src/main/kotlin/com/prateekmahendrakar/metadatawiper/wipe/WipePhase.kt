package com.prateekmahendrakar.metadatawiper.wipe

enum class WipePhase {
    // Copying a source that can't seek, such as a cloud file, to a temp file first.
    Reading,
    Stripping,
    Verifying,
    Writing
}
