package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat

/*
 * Builds small transport streams: a PAT, one program map, then each access unit as one video
 * PES split over as many packets as it takes, the last one stuffed to size. [extraStreams] are
 * listed in the map (stream type and descriptors) but carry no packets. Null packets can be
 * placed after an access unit, to push the next one past the planner's sample.
 */
class TransportStreamBuilder(private val layout: PacketLayout = PacketLayout.Ts) {

    // region State

    var videoStreamType = 0x1B
    var extraStreams = listOf<Pair<Int, ByteArray>>()
    var scrambleVideo = false
    var nullPacketsAfter = mapOf<Int, Int>()

    private val continuity = mutableMapOf<Int, Int>()

    companion object {
        const val VIDEO_PID = 0x100
        const val PMT_PID = 0x1000
        const val NULL_PID = 0x1FFF
        const val PAYLOAD_SIZE = 184

        fun registration(format: String): ByteArray = byteArrayOf(0x05, format.length.toByte()) + format.toByteArray()
    }

    // endregion State

    // region Methods

    fun build(accessUnits: List<ByteArray>): ByteArray {
        val packets = mutableListOf(section(0, programAssociation()), section(PMT_PID, programMap()))
        for ((index, unit) in accessUnits.withIndex()) {
            packets += pes(unit)
            repeat(nullPacketsAfter[index] ?: 0) { packets += packet(NULL_PID, ByteArray(PAYLOAD_SIZE) { 0xFF.toByte() }, false) }
        }
        val prefix = ByteArray(layout.syncOffset)
        return concat(*packets.map { prefix + it }.toTypedArray())
    }

    private fun programAssociation(): ByteArray {
        val body = concat(u16(1), byteArrayOf(0xC1.toByte(), 0, 0), u16(1), u16(0xE000 or PMT_PID))
        return table(0x00, body)
    }

    private fun programMap(): ByteArray {
        val video = stream(videoStreamType, VIDEO_PID, ByteArray(0))
        val extras = extraStreams.mapIndexed { index, (type, descriptors) -> stream(type, VIDEO_PID + 1 + index, descriptors) }
        val body = concat(u16(1), byteArrayOf(0xC1.toByte(), 0, 0), u16(0xE000 or VIDEO_PID), u16(0xF000), video, *extras.toTypedArray())
        return table(0x02, body)
    }

    private fun stream(type: Int,
                       pid: Int,
                       descriptors: ByteArray): ByteArray = byteArrayOf(type.toByte()) + u16(0xE000 or pid) + u16(0xF000 or descriptors.size) + descriptors

    // The section length counts what follows it, CRC included.
    private fun table(id: Int,
                      body: ByteArray): ByteArray {
        val header = byteArrayOf(id.toByte()) + u16(0xB000 or (body.size + 4))
        val section = header + body
        return section + u32(crc32(section))
    }

    private fun section(pid: Int,
                        table: ByteArray): ByteArray {
        val payload = byteArrayOf(0) + table
        return packet(pid, payload + ByteArray(PAYLOAD_SIZE - payload.size) { 0xFF.toByte() }, true)
    }

    // A PES header with a PTS, then the access unit. The last packet is stuffed through its adaptation field.
    private fun pes(unit: ByteArray): List<ByteArray> {
        val header = byteArrayOf(0, 0, 1, 0xE0.toByte(), 0, 0, 0x80.toByte(), 0x80.toByte(), 5, 0x21, 0, 1, 0, 1)
        val data = header + unit
        return (data.indices step PAYLOAD_SIZE).map { start ->
            val chunk = data.copyOfRange(start, minOf(start + PAYLOAD_SIZE, data.size))
            packet(VIDEO_PID, chunk, start == 0, scrambleVideo)
        }
    }

    private fun packet(pid: Int,
                       payload: ByteArray,
                       startsUnit: Boolean,
                       scrambled: Boolean = false): ByteArray {
        val counter = continuity.getOrDefault(pid, 0)
        continuity[pid] = (counter + 1) and 0x0F
        val scrambling = if (scrambled) 0x80 else 0
        val first = byteArrayOf(0x47, ((if (startsUnit) 0x40 else 0) or (pid shr 8)).toByte(), pid.toByte())
        if (payload.size == PAYLOAD_SIZE) {
            return first + byteArrayOf((scrambling or 0x10 or counter).toByte()) + payload
        }
        val stuffing = PAYLOAD_SIZE - 1 - payload.size
        val adaptation = if (stuffing == 0) ByteArray(0) else byteArrayOf(0) + ByteArray(stuffing - 1) { 0xFF.toByte() }
        return first + byteArrayOf((scrambling or 0x30 or counter).toByte(), stuffing.toByte()) + adaptation + payload
    }

    private fun u16(value: Int): ByteArray = byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun u32(value: Int): ByteArray = byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    // CRC-32/MPEG-2, which PSI sections end with.
    private fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 24)
            repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        }
        return crc
    }

    // endregion Methods
}
