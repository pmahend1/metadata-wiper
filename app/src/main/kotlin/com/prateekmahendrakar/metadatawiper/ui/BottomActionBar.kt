package com.prateekmahendrakar.metadatawiper.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun BottomActionBar(secondaryText: String,
                    @DrawableRes secondaryIcon: Int?,
                    secondaryEnabled: Boolean,
                    onSecondary: () -> Unit,
                    primaryText: String,
                    @DrawableRes primaryIcon: Int?,
                    primaryEnabled: Boolean,
                    onPrimary: () -> Unit,
                    note: String? = null) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (note != null) {
                Text(text = note,
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.padding(bottom = 10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSecondary,
                               enabled = secondaryEnabled,
                               modifier = Modifier
                                   .weight(1f)
                                   .height(48.dp)) {
                    ButtonContent(secondaryText, secondaryIcon)
                }
                Button(onClick = onPrimary,
                       enabled = primaryEnabled,
                       modifier = Modifier
                           .weight(1f)
                           .height(48.dp)) {
                    ButtonContent(primaryText, primaryIcon)
                }
            }
        }
    }
}

@Composable
private fun ButtonContent(text: String,
                          @DrawableRes icon: Int?) {
    if (icon != null) {
        Icon(painter = painterResource(id = icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
