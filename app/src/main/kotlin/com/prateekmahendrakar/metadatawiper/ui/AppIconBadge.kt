package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.prateekmahendrakar.metadatawiper.R

// Decorative: the app name always sits next to it, so screen readers skip it.
@Composable
fun AppIconBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(9.dp)
    // The ring keeps the near-black icon from disappearing against a dark top bar.
    Box(modifier = modifier
        .size(32.dp)
        .clip(shape)
        .background(colorResource(id = R.color.app_icon_background))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center) {
        // An adaptive-icon foreground keeps its glyph in the middle two thirds, so it's drawn larger and cropped like a launcher does.
        Image(painter = painterResource(id = R.drawable.app_icon_foreground),
              contentDescription = null,
              modifier = Modifier.requiredSize(48.dp))
    }
}
