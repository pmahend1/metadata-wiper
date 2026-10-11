package com.prateekmahendrakar.metadatawiper.metadata.jpeg

// APPn segments are told apart by the null-terminated identifier at the start of their payload.
class JpegApplicationIdentifier(val marker: Int,
                                identifier: String,
                                val kind: JpegSegmentKind) {

    // region State

    val prefix: ByteArray = identifier.toByteArray(Charsets.ISO_8859_1)

    // endregion State

    // region Methods

    fun matches(payloadStart: ByteArray): Boolean {
        if (payloadStart.size < prefix.size) {
            return false
        }
        return prefix.indices.all { payloadStart[it] == prefix[it] }
    }

    // endregion Methods
}
