package com.prateekmahendrakar.metadatawiper.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.wipe.SelectedFile

/*
 * The sheet is hosted here, once, rather than inside a grid cell: a sheet owned by a lazy item
 * is torn down and recreated whenever the grid recomposes that item, which can make it jump.
 */
@Composable
fun SelectionGrid(files: List<SelectedFile>,
                  privacy: PrivacySettings) {
    var inspectedFile by remember { mutableStateOf<SelectedFile?>(null) }

    LazyVerticalGrid(columns = GridCells.Fixed(3),
                     modifier = Modifier
                         .fillMaxSize()
                         .padding(horizontal = 4.dp),
                     horizontalArrangement = Arrangement.spacedBy(4.dp),
                     verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // The index is part of the key because the same file can be picked twice.
        itemsIndexed(files, key = { index, file -> "$index:${file.uri}" }) { _, file ->
            Image(painter = rememberAsyncImagePainter(file.uri),
                  contentDescription = stringResource(id = R.string.selected_image),
                  contentScale = ContentScale.Fit,
                  modifier = Modifier
                      .aspectRatio(1f)
                      .clickable { inspectedFile = file })
        }
    }

    inspectedFile?.let { file ->
        ImageMetadataDialog(file = file,
                            options = privacy.stripOptions,
                            onDismissRequest = { inspectedFile = null })
    }
}
