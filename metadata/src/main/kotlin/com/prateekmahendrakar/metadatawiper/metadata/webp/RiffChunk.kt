package com.prateekmahendrakar.metadatawiper.metadata.webp

/*
 * One chunk as absolute file offsets. [start] is its FourCC and [end] is exclusive and
 * includes the pad byte that follows odd-sized data, so copying [start, end) copies the
 * chunk verbatim and leaves the next one aligned.
 */
data class RiffChunk(val fourCc: String,
                     val start: Long,
                     val dataStart: Long,
                     val dataLength: Long,
                     val end: Long)
