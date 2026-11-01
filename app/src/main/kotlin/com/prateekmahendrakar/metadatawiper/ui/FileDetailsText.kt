package com.prateekmahendrakar.metadatawiper.ui

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.metadata.FileFormat
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

// "JPEG · 3.2 MB": the detected format, which may differ from what the file name says.
@Composable
fun fileDetailsText(file: SelectedFile): String {
    val size = Formatter.formatShortFileSize(LocalContext.current, file.byteSize)
    return stringResource(id = R.string.file_details, formatName(file.format), size)
}

fun formatName(format: FileFormat): String {
    return when (format) {
        FileFormat.WebP -> "WebP"
        FileFormat.QuickTime -> "MOV"
        FileFormat.ThreeGpp -> "3GP"
        FileFormat.Matroska -> "MKV"
        FileFormat.WebM -> "WebM"
        FileFormat.MpegTs -> "MPEG-TS"
        FileFormat.MpegPs -> "MPEG-PS"
        FileFormat.Unknown -> "?"
        else -> format.name.uppercase()
    }
}
