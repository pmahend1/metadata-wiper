package com.prateekmahendrakar.metadatawiper.metadata.nal

// One message in an SEI NAL unit. The payload is [payloadStart, payloadEnd) in the unit's unescaped payload.
class SeiMessage(val payloadType: Int,
                 val payloadStart: Int,
                 val payloadEnd: Int) {

    // region State

    val payloadSize: Int
        get() = payloadEnd - payloadStart

    // endregion State
}
