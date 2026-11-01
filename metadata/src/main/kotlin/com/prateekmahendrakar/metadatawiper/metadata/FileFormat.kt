package com.prateekmahendrakar.metadatawiper.metadata

enum class FileFormat(val mimeType: String,
                      val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    WebP("image/webp", "webp"),
    Heif("image/heic", "heic"),     // must match the extension, or SAF providers append ".heif" to the name
    Avif("image/avif", "avif"),
    Gif("image/gif", "gif"),
    Bmp("image/bmp", "bmp"),
    Tiff("image/tiff", "tif"),      // detected so we can say "unsupported", not "unknown"
    Mp4("video/mp4", "mp4"),
    QuickTime("video/quicktime", "mov"),
    ThreeGpp("video/3gpp", "3gp"),
    Matroska("video/x-matroska", "mkv"),
    WebM("video/webm", "webm"),
    Avi("video/x-msvideo", "avi"),
    MpegTs("video/mp2t", "ts"),
    MpegPs("video/mpeg", "mpg"),
    Unknown("application/octet-stream", "");

    // region Methods

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    // endregion Methods
}
