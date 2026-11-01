package com.prateekmahendrakar.metadatawiper.metadata.movie

/*
 * A track for MovieBuilder. [chunks] holds each chunk's samples. [sizeBits] picks the size
 * table: 32 writes `stsz` with a size per sample, 0 writes `stsz` with one constant size (all
 * samples must match), and 4, 8 or 16 write compact `stz2`. [largeOffsets] writes `co64`.
 */
class MovieTestTrack(val handler: String,
                     val sampleFormat: String,
                     val chunks: List<List<ByteArray>>,
                     val sizeBits: Int = 32,
                     val largeOffsets: Boolean = false)
