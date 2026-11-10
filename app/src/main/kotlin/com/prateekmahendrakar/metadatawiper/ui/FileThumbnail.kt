package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

// Video frames are decoded on the device by Coil's VideoFrameDecoder, registered in MainActivity.
@Composable
fun FileThumbnail(file: SelectedFile,
                  contentDescription: String?,
                  modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(painter = rememberAsyncImagePainter(file.uri),
              contentDescription = contentDescription,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize())
        if (file.format.isVideo) {
            Box(modifier = Modifier
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = R.drawable.play_arrow_24px),
                     contentDescription = null,
                     tint = Color.White,
                     modifier = Modifier.size(18.dp))
            }
        }
    }
}
