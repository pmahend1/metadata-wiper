package com.prateekmahendrakar.metadatawiper.metadata

enum class MetadataWarningKind {
    // An EXIF block that can't be parsed: its contents can't be listed, but it's still removed.
    DamagedExif,

    // HEIF thumbnails stay, because removing them can stop the image from opening.
    ThumbnailKept,

    // Depth maps, HDR gain maps and other auxiliary images apart from alpha.
    AuxiliaryImagesKept,

    // Subtitles are content, but some drones write per-frame GPS into a subtitle track.
    SubtitleTracksKept,
    ChaptersKept,

    // Matroska fonts that styled subtitles need. Their file names are blanked.
    FontAttachmentsKept,

    // A camera known to write the date and location into the video stream itself, which isn't parsed yet.
    InStreamMetadataNotChecked,

    // Bytes in `mdat` that no track references, in a file with other structures that might. Its one type is the size in bytes.
    UnreferencedDataKept,
    UnknownBoxesKept,
    UnknownItemTypesKept,
    UnknownPropertiesKept,
    UnknownTracksKept,
    UnknownElementsKept
}
