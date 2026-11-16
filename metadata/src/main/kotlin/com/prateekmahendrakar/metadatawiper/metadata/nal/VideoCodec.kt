package com.prateekmahendrakar.metadatawiper.metadata.nal

/*
 * The two codecs whose streams carry SEI messages. They differ only in the NAL unit header: one
 * byte for H.264 with the type in the low five bits, two for HEVC with the type in bits 1–6 of
 * the first. [unspecifiedType] is a type the standard leaves undefined, so decoders skip it.
 */
enum class VideoCodec(val headerSize: Int,
                      private val seiTypes: Set<Int>,
                      private val unspecifiedType: Int) {
    H264(1, setOf(6), 0),
    Hevc(2, setOf(39, 40), 48);

    // region Methods

    fun nalType(firstHeaderByte: Int): Int {
        return when (this) {
            H264 -> firstHeaderByte and 0x1F
            Hevc -> (firstHeaderByte shr 1) and 0x3F
        }
    }

    fun isSei(firstHeaderByte: Int): Boolean = nalType(firstHeaderByte) in seiTypes

    // The header's first byte with its type swapped for the unspecified one. The other header fields stay as they were.
    fun unspecifiedHeaderByte(firstHeaderByte: Int): Byte {
        return when (this) {
            H264 -> ((firstHeaderByte and 0xE0) or unspecifiedType).toByte()
            Hevc -> ((firstHeaderByte and 0x81) or (unspecifiedType shl 1)).toByte()
        }
    }

    // endregion Methods
}
