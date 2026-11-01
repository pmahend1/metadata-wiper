package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.ascii
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.concat
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.cString
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.fullBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.largeBox
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u16
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u32
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u64
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.TestBoxes.u8

/*
 * Builds small synthetic MP4 files with the track layouts ffmpeg won't write on request:
 * telemetry and timecode tracks, `co64`, `stz2`, chunks of several tracks interleaved in
 * `mdat`. Samples are arbitrary bytes; nothing here is decodable media.
 */
class MovieBuilder(private val tracks: List<MovieTestTrack>) {

    // region State

    var majorBrand = "isom"
    var largeMediaData = false
    var moovExtras = listOf<ByteArray>()
    var topLevelExtras = listOf<ByteArray>()
    var trailing = ByteArray(0)

    // Added to every chunk offset, to point samples somewhere they shouldn't be.
    var chunkOffsetShift = 0L

    private companion object {
        const val TIMESCALE = 1000L
        val identityMatrix = concat(u32(0x00010000), u32(0), u32(0),
                                    u32(0), u32(0x00010000), u32(0),
                                    u32(0), u32(0), u32(0x40000000))
    }

    // endregion State

    // region Methods

    fun build(): BuiltMovie {
        val ftyp = box("ftyp", ascii(majorBrand), u32(0), ascii(majorBrand), ascii("mp41"))
        // moov's size doesn't depend on the offsets it holds, so a first pass finds where mdat's data starts.
        val mediaHeaderSize = if (largeMediaData) 16 else 8
        val mediaDataStart = ftyp.size + moov(placeholderOffsets()).size + mediaHeaderSize.toLong()

        val offsets = tracks.map { LongArray(it.chunks.size) }
        val spans = tracks.map { MutableList(it.chunks.size) { FileSpan(0, 0) } }
        val payload = mutableListOf<ByteArray>()
        var position = mediaDataStart
        // Round-robin by chunk index, the way muxers interleave audio and video.
        for (chunk in 0 until (tracks.maxOfOrNull { it.chunks.size } ?: 0)) {
            for ((index, track) in tracks.withIndex()) {
                if (chunk >= track.chunks.size) {
                    continue
                }
                val bytes = concat(*track.chunks[chunk].toTypedArray())
                offsets[index][chunk] = position + chunkOffsetShift
                spans[index][chunk] = FileSpan(position, position + bytes.size)
                payload += bytes
                position += bytes.size
            }
        }

        val mediaPayload = concat(*payload.toTypedArray())
        val mdat = if (largeMediaData) largeBox("mdat", mediaPayload) else box("mdat", mediaPayload)
        return BuiltMovie(concat(ftyp, moov(offsets), mdat, *topLevelExtras.toTypedArray(), trailing), spans)
    }

    private fun placeholderOffsets(): List<LongArray> = tracks.map { LongArray(it.chunks.size) }

    private fun moov(offsets: List<LongArray>): ByteArray {
        val traks = tracks.mapIndexed { index, track -> trak(index + 1, track, offsets[index]) }
        return box("moov", mvhd(), *traks.toTypedArray(), *moovExtras.toTypedArray())
    }

    private fun mvhd(): ByteArray {
        return fullBox("mvhd", 0, 0,
                       u32(0), u32(0), u32(TIMESCALE), u32(0),
                       u32(0x00010000), u16(0x0100), ByteArray(10),
                       identityMatrix, ByteArray(24),
                       u32(tracks.size + 1L))
    }

    private fun trak(trackId: Int,
                     track: MovieTestTrack,
                     offsets: LongArray): ByteArray {
        val tkhd = fullBox("tkhd", 0, 7,
                           u32(0), u32(0), u32(trackId.toLong()), u32(0), u32(0),
                           ByteArray(8), u16(0), u16(0), u16(0), u16(0),
                           identityMatrix, u32(0), u32(0))
        val mdhd = fullBox("mdhd", 0, 0, u32(0), u32(0), u32(TIMESCALE), u32(0), u16(0x55C4), u16(0))
        val hdlr = fullBox("hdlr", 0, 0, u32(0), ascii(track.handler), ByteArray(12), cString(""))
        val minf = box("minf", stbl(track, offsets), *track.minfExtras.toTypedArray())
        return box("trak", tkhd, box("mdia", mdhd, hdlr, minf), *track.trakExtras.toTypedArray())
    }

    private fun stbl(track: MovieTestTrack,
                     offsets: LongArray): ByteArray {
        val samples = track.chunks.flatten()
        val sampleEntry = box(track.sampleFormat, ByteArray(6), u16(1))
        return box("stbl",
                   fullBox("stsd", 0, 0, u32(1), sampleEntry),
                   fullBox("stts", 0, 0, u32(1), u32(samples.size.toLong()), u32(1)),
                   stsc(track),
                   chunkOffsets(track, offsets),
                   sampleSizes(track, samples))
    }

    // One entry per change in samples-per-chunk, as writers compress it.
    private fun stsc(track: MovieTestTrack): ByteArray {
        val entries = mutableListOf<ByteArray>()
        var previous = -1
        for ((index, chunk) in track.chunks.withIndex()) {
            if (chunk.size != previous) {
                entries += concat(u32(index + 1L), u32(chunk.size.toLong()), u32(1))
                previous = chunk.size
            }
        }
        return fullBox("stsc", 0, 0, u32(entries.size.toLong()), *entries.toTypedArray())
    }

    private fun chunkOffsets(track: MovieTestTrack,
                             offsets: LongArray): ByteArray {
        if (track.largeOffsets) {
            return fullBox("co64", 0, 0, u32(offsets.size.toLong()), *offsets.map { u64(it) }.toTypedArray())
        }
        return fullBox("stco", 0, 0, u32(offsets.size.toLong()), *offsets.map { u32(it) }.toTypedArray())
    }

    private fun sampleSizes(track: MovieTestTrack,
                            samples: List<ByteArray>): ByteArray {
        val count = u32(samples.size.toLong())
        return when (track.sizeBits) {
            0 -> {
                val size = samples.map { it.size }.distinct().single()
                fullBox("stsz", 0, 0, u32(size.toLong()), count)
            }
            32 -> fullBox("stsz", 0, 0, u32(0), count, *samples.map { u32(it.size.toLong()) }.toTypedArray())
            16 -> fullBox("stz2", 0, 0, ByteArray(3), u8(16), count, *samples.map { u16(it.size) }.toTypedArray())
            8 -> fullBox("stz2", 0, 0, ByteArray(3), u8(8), count, *samples.map { u8(it.size) }.toTypedArray())
            4 -> fullBox("stz2", 0, 0, ByteArray(3), u8(4), count, packNibbles(samples.map { it.size }))
            else -> throw IllegalArgumentException("Size field of ${track.sizeBits} bits")
        }
    }

    private fun packNibbles(sizes: List<Int>): ByteArray {
        return ByteArray((sizes.size + 1) / 2) { index ->
            val high = sizes[index * 2]
            val low = sizes.getOrElse(index * 2 + 1) { 0 }
            ((high shl 4) or low).toByte()
        }
    }

    // endregion Methods
}
