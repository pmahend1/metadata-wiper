package com.prateekmahendrakar.metadatawiper.metadata.matroska

import com.prateekmahendrakar.metadatawiper.metadata.Iso6709
import com.prateekmahendrakar.metadatawiper.metadata.MalformedFileException
import com.prateekmahendrakar.metadatawiper.metadata.MetadataCategory
import com.prateekmahendrakar.metadatawiper.metadata.MetadataItem
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlElement
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlId
import com.prateekmahendrakar.metadatawiper.metadata.ebml.EbmlReader

/*
 * Lists what a Tags element holds, one item per SimpleTag. It only decides what the user is
 * shown: the element is voided whatever it contains, so a tag that can't be parsed reports the
 * whole element instead of failing the file.
 */
class MatroskaTagReader(private val ebml: EbmlReader) {

    // region State

    private val dates = MetadataCategory.DateTime
    private val people = MetadataCategory.People
    private val software = MetadataCategory.Software
    private val other = MetadataCategory.Other

    private val knownTags = mapOf("DATE_RECORDED" to (dates to MetadataLabel.DATE_TIME_ORIGINAL),
                                  "DATE_DIGITIZED" to (dates to MetadataLabel.DATE_TIME_DIGITIZED),
                                  "DATE_ENCODED" to (dates to MetadataLabel.TIMESTAMP),
                                  "DATE_TAGGED" to (dates to MetadataLabel.TIMESTAMP),
                                  "DATE_WRITTEN" to (dates to MetadataLabel.TIMESTAMP),
                                  "DATE_RELEASED" to (dates to MetadataLabel.TIMESTAMP),
                                  "_STATISTICS_WRITING_DATE_UTC" to (dates to MetadataLabel.TIMESTAMP),
                                  "ARTIST" to (people to MetadataLabel.ARTIST),
                                  "COPYRIGHT" to (people to MetadataLabel.COPYRIGHT),
                                  "ENCODER" to (software to MetadataLabel.SOFTWARE),
                                  "_STATISTICS_WRITING_APP" to (software to MetadataLabel.SOFTWARE),
                                  "TITLE" to (other to MetadataLabel.TITLE),
                                  "COMMENT" to (other to MetadataLabel.COMMENT),
                                  "DESCRIPTION" to (other to MetadataLabel.IMAGE_DESCRIPTION_PROPERTY),
                                  "SUMMARY" to (other to MetadataLabel.IMAGE_DESCRIPTION_PROPERTY),
                                  "SYNOPSIS" to (other to MetadataLabel.IMAGE_DESCRIPTION_PROPERTY))

    // Names of people and organisations. These keep the file's own tag name as their label.
    private val peopleTags = setOf("LEAD_PERFORMER", "ACCOMPANIMENT", "COMPOSER", "ARRANGER", "LYRICIST", "CONDUCTOR",
                                   "DIRECTOR", "ASSISTANT_DIRECTOR", "DIRECTOR_OF_PHOTOGRAPHY", "ACTOR", "WRITTEN_BY",
                                   "SCREENPLAY_BY", "EDITED_BY", "PRODUCER", "COPRODUCER", "EXECUTIVE_PRODUCER",
                                   "ENCODED_BY", "MIXED_BY", "REMIXED_BY", "PRODUCTION_STUDIO", "PUBLISHER", "THANKS_TO")

    // Stream statistics muxers write for every track. They say nothing about who made the file, so listing them is noise.
    private val statisticsTags = setOf("DURATION", "BPS", "NUMBER_OF_FRAMES", "NUMBER_OF_BYTES", "_STATISTICS_TAGS")

    private companion object {
        const val LOCATION = "LOCATION"
    }

    // endregion State

    // region Methods

    fun read(tags: EbmlElement): List<MetadataItem> {
        val found = try {
            ebml.children(tags).filter { it.id == EbmlId.TAG }
                               .flatMap { tag -> ebml.children(tag).filter { it.id == EbmlId.SIMPLE_TAG } }
                               .flatMap { simpleTag(it) }
        } catch (_: MalformedFileException) {
            emptyList()
        }
        if (found.isEmpty()) {
            return listOf(MetadataItem(other, MetadataLabel.TAGS, byteSize = tags.span.length))
        }
        return found
    }

    // A SimpleTag can nest others, which refine it (an ARTIST's URL, say).
    private fun simpleTag(simpleTag: EbmlElement): List<MetadataItem> {
        val children = ebml.children(simpleTag)
        val name = children.firstOrNull { it.id == EbmlId.TAG_NAME }?.let { ebml.readText(it) } ?: ""
        val value = children.firstOrNull { it.id == EbmlId.TAG_STRING }?.let { ebml.readText(it) } ?: ""
        val nested = children.filter { it.id == EbmlId.SIMPLE_TAG }.flatMap { simpleTag(it) }
        if (name.uppercase() in statisticsTags) {
            return nested
        }
        return listOf(item(name, value, simpleTag.span.length)) + nested
    }

    private fun item(name: String,
                     value: String,
                     size: Long): MetadataItem {
        val key = name.uppercase()
        val known = knownTags[key]
        return when {
            key == LOCATION -> MetadataItem(MetadataCategory.Location, MetadataLabel.GPS, Iso6709.coordinates(value).ifEmpty { value }, size)
            known != null -> MetadataItem(known.first, known.second, value, size)
            key in peopleTags -> MetadataItem(people, name, value, size)
            name.isEmpty() -> MetadataItem(other, MetadataLabel.TAGS, value, size)
            else -> MetadataItem(other, name, value, size)
        }
    }

    // endregion Methods
}
