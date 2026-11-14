package com.prateekmahendrakar.metadatawiper.metadata.mpeg

/*
 * The header of one transport stream packet, and where its payload is in [bytes] (the reader's
 * block buffer). A file holds millions of packets, so TransportPacketReader reuses one instance;
 * it's only valid inside the visit it's passed to.
 */
class TransportPacket {

    // region State

    var bytes = ByteArray(0)
        internal set

    // Where the payload's first byte is in the file.
    var payloadFileOffset = 0L
        internal set

    var pid = 0
        internal set

    var startsUnit = false
        internal set

    var isScrambled = false
        internal set

    var payloadStart = 0
        internal set

    var payloadEnd = 0
        internal set

    val hasPayload: Boolean
        get() = payloadEnd > payloadStart

    // endregion State
}
