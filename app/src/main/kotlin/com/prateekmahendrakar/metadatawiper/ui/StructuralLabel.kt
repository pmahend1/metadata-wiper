package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.StringRes
import com.prateekmahendrakar.metadatawiper.R

/*
 * Readable names for the labels the handlers give their findings. Those labels are stable
 * identifiers (mostly TIFF tag names), so a label missing here is shown as it is rather than
 * hidden.
 */
object StructuralLabel {

    // region State

    private val labels = mapOf("GPS" to R.string.label_gps,
                               "Make" to R.string.label_make,
                               "Model" to R.string.label_model,
                               "BodySerialNumber" to R.string.label_serial_number,
                               "LensMake" to R.string.label_lens_make,
                               "LensModel" to R.string.label_lens_model,
                               "LensSerialNumber" to R.string.label_lens_serial_number,
                               "ImageUniqueID" to R.string.label_unique_id,
                               "HostComputer" to R.string.label_host_computer,
                               "MakerNote" to R.string.label_maker_note,
                               "CameraOwnerName" to R.string.label_camera_owner,
                               "Artist" to R.string.label_artist,
                               "Copyright" to R.string.label_copyright,
                               "ImageDescription" to R.string.label_description,
                               "DateTimeOriginal" to R.string.label_taken,
                               "DateTimeDigitized" to R.string.label_digitized,
                               "DateTime" to R.string.label_modified,
                               "Software" to R.string.label_software,
                               "Orientation" to R.string.label_orientation,
                               "Thumbnail" to R.string.label_thumbnail,
                               "JFIF thumbnail" to R.string.label_thumbnail,
                               "ICC profile" to R.string.label_color_profile,
                               "Linked color profile path" to R.string.label_linked_profile,
                               "Data after end of image" to R.string.label_trailer,
                               "Comment" to R.string.label_comment)

    private const val GPS = "GPS"
    private const val ORIENTATION = "Orientation"

    private val orientations = mapOf("1" to R.string.orientation_normal,
                                     "2" to R.string.orientation_mirrored,
                                     "3" to R.string.orientation_rotated_180,
                                     "4" to R.string.orientation_mirrored_vertically,
                                     "5" to R.string.orientation_mirrored_rotated_270,
                                     "6" to R.string.orientation_rotated_90,
                                     "7" to R.string.orientation_mirrored_rotated_90,
                                     "8" to R.string.orientation_rotated_270)

    private val notes = mapOf("Linked color profile path" to R.string.note_linked_profile)

    // endregion State

    // region Methods

    @StringRes
    fun of(label: String): Int? = labels[label]

    @StringRes
    fun valueFor(label: String,
                 value: String): Int? {
        if (label == ORIENTATION) {
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
        if (label == GPS && value.isEmpty()) {
            return R.string.note_gps_hidden
        }
        return notes[label]
    }

    // endregion Methods
}
