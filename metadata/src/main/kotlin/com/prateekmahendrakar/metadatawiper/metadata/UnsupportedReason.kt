package com.prateekmahendrakar.metadatawiper.metadata

enum class UnsupportedReason {
    UnknownFormat,
    NotYetSupported,

    // The format can hold the date and location inside the media stream, where the app can't reach them.
    InStreamMetadata,
    Encrypted,
    ExternalReferences
}
