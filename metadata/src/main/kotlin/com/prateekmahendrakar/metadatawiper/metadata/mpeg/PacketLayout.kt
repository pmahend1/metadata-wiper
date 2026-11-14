package com.prateekmahendrakar.metadatawiper.metadata.mpeg

/*
 * How a transport stream's packets sit in the file. Plain TS packets are 188 bytes; M2TS
 * (AVCHD, Blu-ray) puts a 4-byte arrival timestamp before each, so the sync byte is at 4 of 192.
 */
enum class PacketLayout(val packetSize: Int,
                        val syncOffset: Int) {
    Ts(188, 0),
    M2ts(192, 4)
}
