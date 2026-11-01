package com.prateekmahendrakar.metadatawiper.wipe

import android.webkit.MimeTypeMap
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat

/*
 * SAF providers append an extension when the one in the name doesn't match the MIME type, so an
 * MP4 saved as "clip.m4v" would become "clip.m4v.mp4". The device's own table says what MIME
 * type its providers expect for the name.
 */
object OutputMimeType {

    // region Methods

    fun of(displayName: String,
           format: FileFormat): String {
        val extension = displayName.substringAfterLast('.', "")
        if (extension == format.extension) {
            return format.mimeType
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: format.mimeType
    }

    // endregion Methods
}
