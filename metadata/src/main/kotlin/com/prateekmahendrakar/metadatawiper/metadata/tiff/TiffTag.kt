package com.prateekmahendrakar.metadatawiper.metadata.tiff

object TiffTag {
    val LITTLE_ENDIAN_MARK = byteArrayOf(0x49, 0x49) // "II"
    val BIG_ENDIAN_MARK = byteArrayOf(0x4D, 0x4D)    // "MM"
    const val MAGIC = 42

    const val TYPE_BYTE = 1
    const val TYPE_ASCII = 2
    const val TYPE_SHORT = 3
    const val TYPE_LONG = 4
    const val TYPE_RATIONAL = 5
    const val TYPE_IFD = 13

    const val IMAGE_DESCRIPTION = 0x010E
    const val MAKE = 0x010F
    const val MODEL = 0x0110
    const val ORIENTATION = 0x0112
    const val SOFTWARE = 0x0131
    const val DATE_TIME = 0x0132
    const val ARTIST = 0x013B
    const val HOST_COMPUTER = 0x013C
    const val THUMBNAIL_OFFSET = 0x0201
    const val THUMBNAIL_LENGTH = 0x0202
    const val COPYRIGHT = 0x8298
    const val EXIF_IFD_POINTER = 0x8769
    const val GPS_IFD_POINTER = 0x8825

    const val DATE_TIME_ORIGINAL = 0x9003
    const val DATE_TIME_DIGITIZED = 0x9004
    const val MAKER_NOTE = 0x927C
    const val USER_COMMENT = 0x9286
    const val IMAGE_UNIQUE_ID = 0xA420
    const val CAMERA_OWNER_NAME = 0xA430
    const val BODY_SERIAL_NUMBER = 0xA431
    const val LENS_MAKE = 0xA433
    const val LENS_MODEL = 0xA434
    const val LENS_SERIAL_NUMBER = 0xA435

    const val GPS_LATITUDE_REF = 0x0001
    const val GPS_LATITUDE = 0x0002
    const val GPS_LONGITUDE_REF = 0x0003
    const val GPS_LONGITUDE = 0x0004
}
