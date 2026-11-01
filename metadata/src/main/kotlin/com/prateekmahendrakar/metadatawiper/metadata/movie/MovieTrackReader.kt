package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedFileException
import com.prateekmahendrakar.metadatawiper.metadata.UnsupportedReason
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.BoxReader
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.SampleTableReader

// Reads a `trak` into a MovieTrack, and refuses tracks whose samples can't be handled: encrypted, or kept in another file.
class MovieTrackReader(private val reader: BinaryReader,
                       private val boxReader: BoxReader) {

    // region State

    private val sampleTableReader = SampleTableReader(reader)

    private val encryptedFormats = setOf("encv", "enca", "enct", "encs", "drms", "drmi")

    private companion object {
        const val TKHD = "tkhd"
        const val TREF = "tref"
        const val CHAP = "chap"
        const val MDIA = "mdia"
        const val HDLR = "hdlr"
        const val MINF = "minf"
        const val DINF = "dinf"
        const val DREF = "dref"
        const val STBL = "stbl"
        const val STSD = "stsd"

        const val ENTRY_COUNT_SIZE = 4L
        const val HANDLER_TYPE_OFFSET = 4L
        const val TYPE_SIZE = 4
        const val TRACK_ID_SIZE = 4L
        const val V0_TIMES_SIZE = 8L
        const val V1_TIMES_SIZE = 16L
        const val SELF_CONTAINED = 1L
        const val FLAGS_MASK = 0xFFFFFFL
    }

    // endregion State

    // region Methods

    fun read(trak: Box): MovieTrack {
        val children = boxReader.readChildren(trak)
        val media = required(children, MDIA, trak)
        val mediaChildren = boxReader.readChildren(media)
        val info = required(mediaChildren, MINF, media)
        val infoChildren = boxReader.readChildren(info)
        val stbl = required(infoChildren, STBL, info)
        val sampleEntries = sampleEntries(stbl)

        requireClear(sampleEntries)
        requireSelfContained(infoChildren)
        return MovieTrack(trak,
                          trackId(required(children, TKHD, trak)),
                          handlerType(required(mediaChildren, HDLR, media)),
                          sampleEntries,
                          chapterTrackIds(children),
                          sampleTableReader.read(stbl))
    }

    private fun required(children: List<Box>,
                         type: String,
                         parent: Box): Box {
        return children.firstOrNull { it.type == type }
               ?: throw MalformedFileException("'${parent.type}' box at offset ${parent.start} has no '$type' box")
    }

    private fun sampleEntries(stbl: Box): List<Box> {
        val stsd = required(boxReader.readChildren(stbl), STSD, stbl)
        return boxReader.readFullBoxChildren(stsd, ENTRY_COUNT_SIZE)
    }

    private fun requireClear(sampleEntries: List<Box>) {
        if (sampleEntries.any { it.type in encryptedFormats }) {
            throw UnsupportedFileException(UnsupportedReason.Encrypted, "Encrypted tracks can't be cleaned")
        }
    }

    private fun requireSelfContained(infoChildren: List<Box>) {
        val dinf = infoChildren.firstOrNull { it.type == DINF } ?: return
        val dref = boxReader.readChildren(dinf).firstOrNull { it.type == DREF } ?: return
        for (entry in boxReader.readFullBoxChildren(dref, ENTRY_COUNT_SIZE)) {
            boxReader.fullBoxDataStart(entry)
            reader.seek(entry.dataStart)
            if (reader.readU32() and FLAGS_MASK and SELF_CONTAINED == 0L) {
                throw UnsupportedFileException(UnsupportedReason.ExternalReferences, "Track samples are stored in another file")
            }
        }
    }

    private fun trackId(tkhd: Box): Long {
        val start = boxReader.fullBoxDataStart(tkhd)
        reader.seek(tkhd.dataStart)
        val timesSize = if (reader.readU8() == 1) V1_TIMES_SIZE else V0_TIMES_SIZE
        if (tkhd.end - start < timesSize + TRACK_ID_SIZE) {
            throw MalformedFileException("'tkhd' box at offset ${tkhd.start} is too short")
        }
        reader.seek(start + timesSize)
        return reader.readU32()
    }

    private fun handlerType(hdlr: Box): String {
        val start = boxReader.fullBoxDataStart(hdlr) + HANDLER_TYPE_OFFSET
        if (hdlr.end - start < TYPE_SIZE) {
            throw MalformedFileException("'hdlr' box at offset ${hdlr.start} is too short")
        }
        reader.seek(start)
        return String(reader.readBytes(TYPE_SIZE), Charsets.ISO_8859_1)
    }

    private fun chapterTrackIds(trackChildren: List<Box>): Set<Long> {
        val tref = trackChildren.firstOrNull { it.type == TREF } ?: return emptySet()
        val ids = mutableSetOf<Long>()
        for (reference in boxReader.readChildren(tref).filter { it.type == CHAP }) {
            reader.seek(reference.dataStart)
            for (index in 0 until (reference.end - reference.dataStart) / TRACK_ID_SIZE) {
                ids += reader.readU32()
            }
        }
        return ids
    }

    // endregion Methods
}
