package com.prateekmahendrakar.metadatawiper.metadata.ebml

// Element IDs keep their length-marker bits, as the Matroska specification writes them.
object EbmlId {

    // region State

    const val EBML = 0x1A45DFA3L
    const val VOID = 0xECL
    const val CRC32 = 0xBFL

    const val SEGMENT = 0x18538067L
    const val SEEK_HEAD = 0x114D9B74L
    const val INFO = 0x1549A966L
    const val TRACKS = 0x1654AE6BL
    const val CLUSTER = 0x1F43B675L
    const val CUES = 0x1C53BB6BL
    const val CHAPTERS = 0x1043A770L
    const val ATTACHMENTS = 0x1941A469L
    const val TAGS = 0x1254C367L

    const val SEEK = 0x4DBBL
    const val SEEK_POSITION = 0x53ACL

    const val TIMESTAMP_SCALE = 0x2AD7B1L
    const val DURATION = 0x4489L
    const val CHAPTER_TRANSLATE = 0x6924L
    const val MUXING_APP = 0x4D80L
    const val WRITING_APP = 0x5741L
    const val TITLE = 0x7BA9L
    const val DATE_UTC = 0x4461L
    const val SEGMENT_UUID = 0x73A4L
    const val PREV_UUID = 0x3CB923L
    const val NEXT_UUID = 0x3EB923L
    const val SEGMENT_FAMILY = 0x4444L
    const val SEGMENT_FILENAME = 0x7384L
    const val PREV_FILENAME = 0x3C83ABL
    const val NEXT_FILENAME = 0x3E83BBL

    const val TRACK_ENTRY = 0xAEL
    const val TRACK_TYPE = 0x83L
    const val NAME = 0x536EL
    const val CODEC_NAME = 0x258688L
    const val CONTENT_ENCODINGS = 0x6D80L
    const val CONTENT_ENCODING = 0x6240L
    const val CONTENT_ENCRYPTION = 0x5035L

    const val SIMPLE_BLOCK = 0xA3L
    const val BLOCK_GROUP = 0xA0L
    const val ENCRYPTED_BLOCK = 0xAFL

    const val EDITION_ENTRY = 0x45B9L
    const val CHAPTER_ATOM = 0xB6L
    const val CHAPTER_DISPLAY = 0x80L
    const val CHAP_STRING = 0x85L

    const val ATTACHED_FILE = 0x61A7L
    const val FILE_NAME = 0x466EL
    const val FILE_MEDIA_TYPE = 0x4660L
    const val FILE_DESCRIPTION = 0x467EL

    const val TAG = 0x7373L
    const val SIMPLE_TAG = 0x67C8L
    const val TAG_NAME = 0x45A3L
    const val TAG_STRING = 0x4487L

    // What may follow a Cluster in a Segment. One of these ends a Cluster whose size is unknown.
    val segmentChildren = setOf(SEEK_HEAD, INFO, TRACKS, CLUSTER, CUES, CHAPTERS, ATTACHMENTS, TAGS)

    // endregion State
}
