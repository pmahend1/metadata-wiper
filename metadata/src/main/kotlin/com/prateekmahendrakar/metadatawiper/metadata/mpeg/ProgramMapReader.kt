package com.prateekmahendrakar.metadatawiper.metadata.mpeg

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import java.io.ByteArrayOutputStream

/*
 * Lists the elementary streams of every program: the program association table (PID 0) names
 * each program's map PID, and each map lists its streams. Writers repeat both every fraction of
 * a second from the start of the file, so reading stops as soon as all of them have been seen.
 * A table split over several sections isn't read, because a missed section could hide a video
 * stream that would then go unscanned.
 */
class ProgramMapReader(private val packets: TransportPacketReader) {

    // region State

    private val sections = mutableMapOf<Int, ByteArrayOutputStream>()
    private var mapPids: Set<Int>? = null
    private val streams = mutableMapOf<Int, ElementaryStream>()
    private val readMaps = mutableSetOf<Int>()

    private companion object {
        const val PAT_PID = 0
        const val PAT_TABLE = 0x00
        const val PMT_TABLE = 0x02
        const val SECTION_HEADER_SIZE = 3
        const val CRC_SIZE = 4
        const val LENGTH_MASK = 0x0FFF
        const val PID_MASK = 0x1FFF

        // Offsets in a section: the section numbers, then the PAT's entries or the PMT's program info length.
        const val SECTION_NUMBER_OFFSET = 6
        const val LAST_SECTION_NUMBER_OFFSET = 7
        const val PAT_ENTRIES_OFFSET = 8
        const val PAT_ENTRY_SIZE = 4
        const val PROGRAM_INFO_LENGTH_OFFSET = 10
        const val PMT_DESCRIPTORS_OFFSET = 12
        const val STREAM_ENTRY_SIZE = 5

        const val REGISTRATION_DESCRIPTOR = 0x05
        const val KLV_FORMAT = "KLVA"
    }

    // endregion State

    // region Methods

    fun read(): List<ElementaryStream> {
        packets.read { packet ->
            if (packet.hasPayload) {
                feed(packet)
            }
            val maps = mapPids
            maps == null || readMaps.containsAll(maps) == false
        }
        val maps = mapPids ?: throw MalformedFileException("Transport stream has no program association table")
        if (readMaps.containsAll(maps) == false) {
            throw MalformedFileException("Transport stream is missing the program map for PIDs ${maps - readMaps}")
        }
        return streams.values.sortedBy { it.pid }
    }

    private fun feed(packet: TransportPacket) {
        val pid = packet.pid
        val isTable = pid == PAT_PID || (mapPids?.contains(pid) == true && pid !in readMaps)
        if (isTable == false) {
            return
        }

        // A section starts after the pointer field of a unit-start packet; its tail can carry on into the next packets.
        val bytes = packet.bytes
        if (packet.startsUnit) {
            val pointer = bytes[packet.payloadStart].toInt() and 0xFF
            val start = packet.payloadStart + 1 + pointer
            if (start >= packet.payloadEnd) {
                throw MalformedFileException("Table section on PID $pid starts past its packet")
            }
            sections[pid] = ByteArrayOutputStream().also { it.write(bytes, start, packet.payloadEnd - start) }
        } else {
            sections[pid]?.write(bytes, packet.payloadStart, packet.payloadEnd - packet.payloadStart)
        }

        val section = sections[pid]?.toByteArray() ?: return
        if (section.size < SECTION_HEADER_SIZE) {
            return
        }
        val length = SECTION_HEADER_SIZE + (u16(section, 1) and LENGTH_MASK)
        if (section.size < length) {
            return
        }
        sections.remove(pid)
        parse(pid, section.copyOf(length))
    }

    private fun parse(pid: Int,
                      section: ByteArray) {
        val table = section[0].toInt() and 0xFF
        when {
            pid == PAT_PID && table == PAT_TABLE && mapPids == null -> mapPids = readAssociations(section)
            pid != PAT_PID && table == PMT_TABLE -> readMap(pid, section)
        }
    }

    private fun readAssociations(section: ByteArray): Set<Int> {
        requireSingleSection(section)
        val pids = mutableSetOf<Int>()
        for (offset in PAT_ENTRIES_OFFSET until section.size - CRC_SIZE - PAT_ENTRY_SIZE + 1 step PAT_ENTRY_SIZE) {
            // Program 0 names the network information table, not a program.
            if (u16(section, offset) != 0) {
                pids += u16(section, offset + 2) and PID_MASK
            }
        }
        return pids
    }

    private fun readMap(pid: Int,
                        section: ByteArray) {
        requireSingleSection(section)
        val end = section.size - CRC_SIZE
        if (end < PMT_DESCRIPTORS_OFFSET) {
            throw MalformedFileException("Program map on PID $pid is too short")
        }
        var offset = PMT_DESCRIPTORS_OFFSET + (u16(section, PROGRAM_INFO_LENGTH_OFFSET) and LENGTH_MASK)
        if (offset > end) {
            throw MalformedFileException("Program map on PID $pid has program descriptors past its end")
        }
        while (offset < end) {
            if (offset + STREAM_ENTRY_SIZE > end) {
                throw MalformedFileException("Program map on PID $pid has a truncated stream entry")
            }
            val streamType = section[offset].toInt() and 0xFF
            val streamPid = u16(section, offset + 1) and PID_MASK
            val infoLength = u16(section, offset + 3) and LENGTH_MASK
            val descriptors = offset + STREAM_ENTRY_SIZE
            if (descriptors + infoLength > end) {
                throw MalformedFileException("Program map on PID $pid has stream descriptors past its end")
            }
            streams.putIfAbsent(streamPid, ElementaryStream(streamPid, streamType, hasKlvRegistration(section, descriptors, descriptors + infoLength)))
            offset = descriptors + infoLength
        }
        readMaps += pid
    }

    private fun requireSingleSection(section: ByteArray) {
        if (section.size < PAT_ENTRIES_OFFSET + CRC_SIZE) {
            throw MalformedFileException("Table section is too short")
        }
        if (section[SECTION_NUMBER_OFFSET] != 0.toByte() || section[LAST_SECTION_NUMBER_OFFSET] != 0.toByte()) {
            throw UnsupportedFileException(UnsupportedReason.NotYetSupported, "Program tables split over several sections aren't supported")
        }
    }

    private fun hasKlvRegistration(section: ByteArray,
                                   start: Int,
                                   end: Int): Boolean {
        var offset = start
        while (offset + 2 <= end) {
            val tag = section[offset].toInt() and 0xFF
            val length = section[offset + 1].toInt() and 0xFF
            val data = offset + 2
            if (data + length > end) {
                throw MalformedFileException("Stream descriptor runs past its entry")
            }
            if (tag == REGISTRATION_DESCRIPTOR && length >= KLV_FORMAT.length && String(section, data, KLV_FORMAT.length, Charsets.ISO_8859_1) == KLV_FORMAT) {
                return true
            }
            offset = data + length
        }
        return false
    }

    private fun u16(bytes: ByteArray,
                    offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    // endregion Methods
}
