package com.prateekmahendrakar.metadatawiper.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.model.Theme

@Composable
fun SettingsDialog(
    currentTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    onDismiss: () -> Unit,
    currentOverwriteOriginal: Boolean,
    onOverwriteOriginalChange: (Boolean) -> Unit,
    appVersion: String
) {
    var tempTheme by remember { mutableStateOf(currentTheme) }
    var tempOverwrite by remember { mutableStateOf(currentOverwriteOriginal) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(id = R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // --- Theme Section ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(id = R.string.theme),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Theme.entries.forEach { theme ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (theme == tempTheme),
                                    onClick = { tempTheme = theme }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (theme == tempTheme),
                                onClick = null // The Row's selectable modifier handles the click
                            )
                            val themeText = when (theme) {
                                Theme.System -> stringResource(id = R.string.theme_system)
                                Theme.Light -> stringResource(id = R.string.theme_light)
                                Theme.Dark -> stringResource(id = R.string.theme_dark)
                            }
                            Text(
                                text = themeText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- File Options Section ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(id = R.string.file_options),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(id = R.string.overwrite_original_file),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = tempOverwrite,
                            onCheckedChange = { tempOverwrite = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- Rate and Review Section ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val packageName = context.packageName
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "market://details?id=$packageName".toUri()
                                    ).addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                )
                            } catch (e: ActivityNotFoundException) {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "http://play.google.com/store/apps/details?id=$packageName".toUri()
                                    )
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.star_24px),
                        contentDescription = stringResource(id = R.string.rate_and_review),
                    )
                    Text(
                        text = stringResource(id = R.string.rate_and_review),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // --- Privacy Policy Section ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://pmahend1.github.io/metadata-wiper/privacy-policy.html".toUri()
                            )
                            context.startActivity(intent)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.policy_24px),
                        contentDescription = stringResource(id = R.string.privacy_policy),
                    )
                    Text(
                        text = stringResource(id = R.string.privacy_policy),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // --- App Version Section ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(id = R.string.app_version),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = appVersion,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))


                // --- Close Button ---
                Button(
                    onClick = {
                        onThemeChange(tempTheme)
                        onOverwriteOriginalChange(tempOverwrite)
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(id = R.string.close))
                }
            }
        }
    }
}
