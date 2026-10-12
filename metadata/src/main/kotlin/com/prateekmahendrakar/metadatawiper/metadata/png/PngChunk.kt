package com.prateekmahendrakar.metadatawiper.metadata.png

/*
 * One chunk as absolute file offsets. [start] is its length field and [end] is exclusive and
 * includes the CRC, so copying [start, end) copies the chunk verbatim.
 */
data class PngChunk(val type: String,
                    val start: Long,
                    val dataStart: Long,
                    val dataLength: Long,
                    val end: Long)
