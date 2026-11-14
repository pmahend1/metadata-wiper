package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader

/*
 * Reads a transport stream packet by packet, in blocks so a multi-gigabyte file costs one read
 * per few hundred kilobytes. Every packet must start with the sync byte where the layout puts it
 * and the file must end on a packet boundary: there's no resynchronising, since bytes that
 * can't be placed in a packet can't be shown to hold no video.
 */
class TransportPacketReader(private val reader: BinaryReader) {

    // region State

    val layout: PacketLayout = detectLayout()

    private val packet = TransportPacket()

    private companion object {
        const val SYNC_BYTE = 0x47
        const val TS_PACKET_SIZE = 188
        const val HEADER_SIZE = 4
        const val PACKETS_PER_BLOCK = 2048
        const val SYNC_CHECKS = 3

        const val PID_MASK = 0x1FFF
        const val UNIT_START = 0x40
        const val SCRAMBLING_MASK = 0xC0
        const val ADAPTATION_FIELD = 0x20
        const val PAYLOAD = 0x10
    }

    // endregion State

    // region Methods

    // Calls [visit] for each packet in file order until it returns false.
    fun read(visit: (TransportPacket) -> Boolean) {
        val packetSize = layout.packetSize
        var blockStart = 0L
        while (blockStart < reader.size) {
            val blockSize = minOf(reader.size - blockStart, packetSize.toLong() * PACKETS_PER_BLOCK).toInt()
            reader.seek(blockStart)
            val block = reader.readBytes(blockSize)
            for (start in 0 until blockSize step packetSize) {
                parse(block, start + layout.syncOffset, blockStart + start + layout.syncOffset)
                if (visit(packet) == false) {
                    return
                }
            }
            blockStart += blockSize
        }
    }

    private fun detectLayout(): PacketLayout {
        for (layout in PacketLayout.entries) {
            if (reader.size == 0L || reader.size % layout.packetSize != 0L) {
                continue
            }
            val checks = minOf(SYNC_CHECKS.toLong(), reader.size / layout.packetSize)
            val synced = (0 until checks).all {
                reader.seek(it * layout.packetSize + layout.syncOffset)
                reader.readU8() == SYNC_BYTE
            }
            if (synced) {
                return layout
            }
        }
        throw MalformedFileException("Transport stream isn't a whole number of 188- or 192-byte packets")
    }

    private fun parse(block: ByteArray,
                      sync: Int,
                      syncFileOffset: Long) {
        if (block[sync].toInt() and 0xFF != SYNC_BYTE) {
            throw MalformedFileException("Transport packet at offset $syncFileOffset has no sync byte")
        }
        val flags = block[sync + 1].toInt() and 0xFF
        val control = block[sync + 3].toInt() and 0xFF
        var payloadStart = sync + HEADER_SIZE
        if (control and ADAPTATION_FIELD != 0) {
            val adaptationLength = block[payloadStart].toInt() and 0xFF
            payloadStart += 1 + adaptationLength
            if (payloadStart > sync + TS_PACKET_SIZE) {
                throw MalformedFileException("Transport packet at offset $syncFileOffset has an adaptation field past its end")
            }
        }
        packet.bytes = block
        packet.pid = ((flags shl 8) or (block[sync + 2].toInt() and 0xFF)) and PID_MASK
        packet.startsUnit = flags and UNIT_START != 0
        packet.isScrambled = control and SCRAMBLING_MASK != 0
        packet.payloadStart = payloadStart
        packet.payloadEnd = if (control and PAYLOAD != 0) sync + TS_PACKET_SIZE else payloadStart
        packet.payloadFileOffset = syncFileOffset + (payloadStart - sync)
    }

    // endregion Methods
}
