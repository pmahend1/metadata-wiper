package com.prateekmahendrakar.metadatawiper.metadata

enum class MetadataWarningKind {
    // An EXIF block that can't be parsed: its contents can't be listed, but it's still removed.
    DamagedExif,

    // HEIF thumbnails stay, because removing them can stop the image from opening.
    ThumbnailKept,

    // Depth maps, HDR gain maps and other auxiliary images apart from alpha.
    AuxiliaryImagesKept,

    // An image sequence's track timestamps, until video support lands.
    SequenceTimestampsKept,
    UnknownBoxesKept,
    UnknownItemTypesKept,
    UnknownPropertiesKept
}
