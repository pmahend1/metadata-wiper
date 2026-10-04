package com.prateekmahendrakar.metadatawiper.metadata

enum class FileFormat(val mimeType: String,
                      val extension: String) {
    Jpeg("image/jpeg", "jpg"),
    Png("image/png", "png"),
    WebP("image/webp", "webp"),
    Heif("image/heif", "heic"),
    Avif("image/avif", "avif"),
    Gif("image/gif", "gif"),
    Bmp("image/bmp", "bmp"),
    Tiff("image/tiff", "tif"),      // detected so we can say "unsupported", not "unknown"
    Unknown("application/octet-stream", "")
}