package com.prateekmahendrakar.metadatawiper.wipe

// Why a file wasn't cleaned. An enum rather than a message so the UI can localize it.
enum class WipeFailure {
    Unreadable,
    Malformed,

    // The stripped output still re-inspects with metadata the options don't keep.
    MetadataRemained,

    // The stripped output is empty, larger than expected, or doesn't parse.
    InvalidOutput,
    DecodeFailed,
    WriteFailed,
    Unexpected
}
