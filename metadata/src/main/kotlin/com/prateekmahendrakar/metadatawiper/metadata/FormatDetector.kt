package com.prateekmahendrakar.metadatawiper.metadata

class FormatDetector {
    private val jpegSignature = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val gif89Signature = "GIF89a".toByteArray(Charsets.US_ASCII)
    private val gif87Signature = "GIF87a".toByteArray(Charsets.US_ASCII)
    private val pngSignature = byteArrayOf(
        0x89.toByte(),
        0x50.toByte(),
        0x4E.toByte(),
        0x47.toByte(),
        0x0D.toByte(),
        0x0A.toByte(),
        0x1A.toByte(),
        0x0A.toByte()
    )

    private fun ByteArray.hasSignature(
        signature: ByteArray,
        offset: Int = 0
    ): Boolean {
        if (size < offset + signature.size) {
            return false
        }
        return signature.indices.all { this[offset + it] == signature[it] }
    }

    fun detect(header: ByteArray): FileFormat {
        if (header.hasSignature(jpegSignature)) {
            return FileFormat.Jpeg
        }

        if (header.hasSignature(gif87Signature) || header.hasSignature(gif89Signature)) {
            return FileFormat.Gif
        }

        if (header.hasSignature(pngSignature)) {
            return FileFormat.Png
        }
        return FileFormat.Unknown
    }
}