package com.prateekmahendrakar.metadatawiper.metadata.movie

/*
 * A track for MovieBuilder. [chunks] holds each chunk's samples. [sizeBits] picks the size
 * table: 32 writes `stsz` with a size per sample, 0 writes `stsz` with one constant size (all
 * samples must match), and 4, 8 or 16 write compact `stz2`. [largeOffsets] writes `co64`.
 * [trakExtras], [minfExtras] and [stblExtras] are added as they are to the end of those boxes,
 * and [sampleEntryExtras] after the sample entry's data reference index. [offsetShift] moves this
 * track's chunk offsets, to point its samples somewhere they shouldn't be.
 */
class MovieTestTrack(val handler: String,
                     val sampleFormat: String,
                     val chunks: List<List<ByteArray>>,
                     val sizeBits: Int = 32,
                     val largeOffsets: Boolean = false,
                     val trakExtras: List<ByteArray> = emptyList(),
                     val minfExtras: List<ByteArray> = emptyList(),
                     val stblExtras: List<ByteArray> = emptyList(),
                     val sampleEntryExtras: ByteArray = ByteArray(0),
                     val offsetShift: Long = 0)
