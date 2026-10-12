package com.prateekmahendrakar.metadatawiper.metadata.png

object PngChunkType {
    const val IHDR = "IHDR"
    const val PLTE = "PLTE"
    const val IDAT = "IDAT"
    const val IEND = "IEND"

    const val ICCP = "iCCP"
    const val EXIF = "eXIf"
    const val TIME = "tIME"
    const val TEXT = "tEXt"
    const val COMPRESSED_TEXT = "zTXt"
    const val INTERNATIONAL_TEXT = "iTXt"

    // Rendering hints and APNG animation control; none of them describe where or by whom.
    val rendering = setOf("tRNS", "gAMA", "cHRM", "sRGB", "sBIT", "cICP", "mDCV", "cLLI", "pHYs", "bKGD")
    val animation = setOf("acTL", "fcTL", "fdAT")
}
