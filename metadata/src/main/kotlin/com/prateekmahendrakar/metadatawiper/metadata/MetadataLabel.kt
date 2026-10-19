package com.prateekmahendrakar.metadatawiper.metadata

/*
 * Every fixed label a handler gives a MetadataItem. They're stable identifiers, not display
 * text: the app maps each one to a translated string, and its tests check that none is missed.
 * Labels taken from the file itself (PNG text keywords) aren't listed.
 */
object MetadataLabel {

    // region State

    const val EXIF_BLOCK = "EXIF block"
    const val XMP = "XMP"
    const val EXTENDED_XMP = "Extended XMP"
    const val MULTI_PICTURE_INDEX = "Multi-picture index"
    const val FLASHPIX_DATA = "FlashPix data"
    const val IPTC_PHOTOSHOP = "IPTC / Photoshop"
    const val APPLICATION_SEGMENT = "Application segment"
    const val JFIF_EXTENSION_THUMBNAIL = "JFIF extension thumbnail"
    const val JFIF_THUMBNAIL = "JFIF thumbnail"
    const val COMMENT = "Comment"
    const val ICC_PROFILE = "ICC profile"
    const val TRAILING_DATA = "Data after end of image"
    const val IMAGE_DESCRIPTION_PROPERTY = "Image description"
    const val ALTERNATIVE_TEXT = "Alternative text"
    const val CREATION_TIME = "Creation time"
    const val MODIFICATION_TIME = "Modification time"
    const val TIMESTAMP = "Timestamp"
    const val MOTION_PHOTO_VIDEO = "Motion photo video"
    const val UNKNOWN_BOX = "Unknown box"
    const val XML_METADATA = "XML metadata"
    const val LINKED_DATA = "Linked data"
    const val REGION_ANNOTATIONS = "Region annotations"
    const val ITEM_NAMES = "Item names"
    const val EMBEDDED_DATA = "Embedded data"
    const val ANCILLARY_CHUNK = "Ancillary chunk"
    const val TEXT = "Text"
    const val EXTENSION = "Extension"
    const val APPLICATION_EXTENSION = "Application extension"
    const val UNKNOWN_CHUNK = "Unknown chunk"
    const val LINKED_PROFILE_PATH = "Linked color profile path"
    const val GPS = "GPS"
    const val ORIENTATION = "Orientation"
    const val THUMBNAIL = "Thumbnail"
    const val MAKER_NOTE = "MakerNote"
    const val USER_COMMENT = "UserComment"
    const val IMAGE_DESCRIPTION = "ImageDescription"
    const val MAKE = "Make"
    const val MODEL = "Model"
    const val SOFTWARE = "Software"
    const val DATE_TIME = "DateTime"
    const val ARTIST = "Artist"
    const val HOST_COMPUTER = "HostComputer"
    const val COPYRIGHT = "Copyright"
    const val DATE_TIME_ORIGINAL = "DateTimeOriginal"
    const val DATE_TIME_DIGITIZED = "DateTimeDigitized"
    const val IMAGE_UNIQUE_ID = "ImageUniqueID"
    const val CAMERA_OWNER_NAME = "CameraOwnerName"
    const val BODY_SERIAL_NUMBER = "BodySerialNumber"
    const val LENS_MAKE = "LensMake"
    const val LENS_MODEL = "LensModel"
    const val LENS_SERIAL_NUMBER = "LensSerialNumber"

    val all = listOf(EXIF_BLOCK,
                     XMP,
                     EXTENDED_XMP,
                     MULTI_PICTURE_INDEX,
                     FLASHPIX_DATA,
                     IPTC_PHOTOSHOP,
                     APPLICATION_SEGMENT,
                     JFIF_EXTENSION_THUMBNAIL,
                     JFIF_THUMBNAIL,
                     COMMENT,
                     ICC_PROFILE,
                     TRAILING_DATA,
                     IMAGE_DESCRIPTION_PROPERTY,
                     ALTERNATIVE_TEXT,
                     CREATION_TIME,
                     MODIFICATION_TIME,
                     TIMESTAMP,
                     MOTION_PHOTO_VIDEO,
                     UNKNOWN_BOX,
                     XML_METADATA,
                     LINKED_DATA,
                     REGION_ANNOTATIONS,
                     ITEM_NAMES,
                     EMBEDDED_DATA,
                     ANCILLARY_CHUNK,
                     TEXT,
                     EXTENSION,
                     APPLICATION_EXTENSION,
                     UNKNOWN_CHUNK,
                     LINKED_PROFILE_PATH,
                     GPS,
                     ORIENTATION,
                     THUMBNAIL,
                     MAKER_NOTE,
                     USER_COMMENT,
                     IMAGE_DESCRIPTION,
                     MAKE,
                     MODEL,
                     SOFTWARE,
                     DATE_TIME,
                     ARTIST,
                     HOST_COMPUTER,
                     COPYRIGHT,
                     DATE_TIME_ORIGINAL,
                     DATE_TIME_DIGITIZED,
                     IMAGE_UNIQUE_ID,
                     CAMERA_OWNER_NAME,
                     BODY_SERIAL_NUMBER,
                     LENS_MAKE,
                     LENS_MODEL,
                     LENS_SERIAL_NUMBER)

    // endregion State
}
