package com.prateekmahendrakar.metadatawiper.metadata.jpeg

/*
 * One marker and everything up to the next one, as absolute file offsets. [start] is the
 * 0xFF that opens the marker; [end] is exclusive. For a Scan, the range also covers the
 * entropy-coded data and its RSTn markers, so copying [start, end) copies the whole scan.
 */
data class JpegSegment(val marker: Int,
                       val kind: JpegSegmentKind,
                       val start: Long,
                       val payloadStart: Long,
                       val payloadLength: Long,
                       val end: Long)
