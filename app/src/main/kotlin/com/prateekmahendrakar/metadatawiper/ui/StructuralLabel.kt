package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.StringRes
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.MetadataLabel

// Translated names for the labels handlers give their findings. A label missing here is shown as it is rather than hidden.
object StructuralLabel {

    // region State

    // The predefined PNG text keywords. Other keywords are the file's own words and stay as they are.
    private val pngKeywords = mapOf("Author" to R.string.label_author,
                                    "Title" to R.string.label_title,
                                    "Description" to R.string.label_description,
                                    "Creation Time" to R.string.label_created,
                                    "Source" to R.string.label_source)

    val labels: Map<String, Int> = pngKeywords + mapOf(MetadataLabel.EXIF_BLOCK to R.string.label_exif_block,
                                                       MetadataLabel.XMP to R.string.label_xmp,
                                                       MetadataLabel.EXTENDED_XMP to R.string.label_extended_xmp,
                                                       MetadataLabel.MULTI_PICTURE_INDEX to R.string.label_multi_picture,
                                                       MetadataLabel.FLASHPIX_DATA to R.string.label_flashpix,
                                                       MetadataLabel.IPTC_PHOTOSHOP to R.string.label_iptc,
                                                       MetadataLabel.APPLICATION_SEGMENT to R.string.label_application_data,
                                                       MetadataLabel.JFIF_EXTENSION_THUMBNAIL to R.string.label_extra_thumbnail,
                                                       MetadataLabel.JFIF_THUMBNAIL to R.string.label_thumbnail,
                                                       MetadataLabel.COMMENT to R.string.label_comment,
                                                       MetadataLabel.ICC_PROFILE to R.string.label_color_profile,
                                                       MetadataLabel.TRAILING_DATA to R.string.label_trailer,
                                                       MetadataLabel.IMAGE_DESCRIPTION_PROPERTY to R.string.label_description,
                                                       MetadataLabel.ALTERNATIVE_TEXT to R.string.label_alternative_text,
                                                       MetadataLabel.CREATION_TIME to R.string.label_created,
                                                       MetadataLabel.MODIFICATION_TIME to R.string.label_modified,
                                                       MetadataLabel.TIMESTAMP to R.string.label_timestamp,
                                                       MetadataLabel.MOTION_PHOTO_VIDEO to R.string.label_motion_photo,
                                                       MetadataLabel.UNKNOWN_BOX to R.string.label_unrecognized,
                                                       MetadataLabel.XML_METADATA to R.string.label_xml,
                                                       MetadataLabel.LINKED_DATA to R.string.label_linked_data,
                                                       MetadataLabel.REGION_ANNOTATIONS to R.string.label_regions,
                                                       MetadataLabel.ITEM_NAMES to R.string.label_item_names,
                                                       MetadataLabel.EMBEDDED_DATA to R.string.label_embedded_data,
                                                       MetadataLabel.ANCILLARY_CHUNK to R.string.label_extra_chunk,
                                                       MetadataLabel.TEXT to R.string.label_text,
                                                       MetadataLabel.EXTENSION to R.string.label_extension,
                                                       MetadataLabel.APPLICATION_EXTENSION to R.string.label_application_data,
                                                       MetadataLabel.UNKNOWN_CHUNK to R.string.label_unrecognized,
                                                       MetadataLabel.LINKED_PROFILE_PATH to R.string.label_linked_profile,
                                                       MetadataLabel.GPS to R.string.label_gps,
                                                       MetadataLabel.ORIENTATION to R.string.label_orientation,
                                                       MetadataLabel.THUMBNAIL to R.string.label_thumbnail,
                                                       MetadataLabel.MAKER_NOTE to R.string.label_maker_note,
                                                       MetadataLabel.USER_COMMENT to R.string.label_comment,
                                                       MetadataLabel.IMAGE_DESCRIPTION to R.string.label_description,
                                                       MetadataLabel.MAKE to R.string.label_make,
                                                       MetadataLabel.MODEL to R.string.label_model,
                                                       MetadataLabel.SOFTWARE to R.string.label_software,
                                                       MetadataLabel.DATE_TIME to R.string.label_modified,
                                                       MetadataLabel.ARTIST to R.string.label_artist,
                                                       MetadataLabel.HOST_COMPUTER to R.string.label_host_computer,
                                                       MetadataLabel.COPYRIGHT to R.string.label_copyright,
                                                       MetadataLabel.DATE_TIME_ORIGINAL to R.string.label_taken,
                                                       MetadataLabel.DATE_TIME_DIGITIZED to R.string.label_digitized,
                                                       MetadataLabel.IMAGE_UNIQUE_ID to R.string.label_unique_id,
                                                       MetadataLabel.CAMERA_OWNER_NAME to R.string.label_camera_owner,
                                                       MetadataLabel.BODY_SERIAL_NUMBER to R.string.label_serial_number,
                                                       MetadataLabel.LENS_MAKE to R.string.label_lens_make,
                                                       MetadataLabel.LENS_MODEL to R.string.label_lens_model,
                                                       MetadataLabel.LENS_SERIAL_NUMBER to R.string.label_lens_serial_number)

    private val notes = mapOf(MetadataLabel.LINKED_PROFILE_PATH to R.string.note_linked_profile)

    private val orientations = mapOf("1" to R.string.orientation_normal,
                                     "2" to R.string.orientation_mirrored,
                                     "3" to R.string.orientation_rotated_180,
                                     "4" to R.string.orientation_mirrored_vertically,
                                     "5" to R.string.orientation_mirrored_rotated_270,
                                     "6" to R.string.orientation_rotated_90,
                                     "7" to R.string.orientation_mirrored_rotated_90,
                                     "8" to R.string.orientation_rotated_270)

    // endregion State

    // region Methods

    @StringRes
    fun of(label: String): Int? = labels[label]

    @StringRes
    fun valueFor(label: String,
                 value: String): Int? {
        if (label == MetadataLabel.ORIENTATION) {
            return orientations[value]
        }
        return null
    }

    /*
     * Android zeroes GPS values for apps without ACCESS_MEDIA_LOCATION, even through the
     * document picker, so a GPS block can arrive with no readable position. It's still there in
     * the file on disk, and still removed.
     */
    @StringRes
    fun noteFor(label: String,
                value: String): Int? {
        if (label == MetadataLabel.GPS && value.isEmpty()) {
            return R.string.note_gps_hidden
        }
        return notes[label]
    }

    // endregion Methods
}
