package com.prateekmahendrakar.metadatawiper.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.prateekmahendrakar.metadatawiper.R
import com.prateekmahendrakar.metadatawiper.model.PrivacySettings
import com.prateekmahendrakar.metadatawiper.model.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(theme: Theme,
                   onThemeChange: (Theme) -> Unit,
                   overwriteOriginal: Boolean,
                   onOverwriteOriginalChange: (Boolean) -> Unit,
                   privacy: PrivacySettings,
                   onKeepColorProfileChange: (Boolean) -> Unit,
                   onKeepOrientationChange: (Boolean) -> Unit,
                   onRandomizeFileNamesChange: (Boolean) -> Unit,
                   appVersion: String,
                   onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(id = R.string.settings)) },
                  navigationIcon = {
                      IconButton(onClick = onBack) {
                          Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = stringResource(id = R.string.back))
                      }
                  })
    }) { padding ->
        Column(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)) {
            SectionTitle(R.string.theme)
            SingleChoiceSegmentedButtonRow(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)) {
                Theme.entries.forEachIndexed { index, option ->
                    SegmentedButton(selected = option == theme,
                                    onClick = { onThemeChange(option) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Theme.entries.size)) {
                        Text(stringResource(id = themeLabel(option)))
                    }
                }
            }

            SectionTitle(R.string.saving)
            SwitchRow(title = R.string.overwrite_original_file,
                      description = R.string.overwrite_original_description,
                      checked = overwriteOriginal,
                      onCheckedChange = onOverwriteOriginalChange)

            SectionTitle(R.string.privacy)
            SwitchRow(title = R.string.keep_color_profile,
                      description = R.string.keep_color_profile_description,
                      checked = privacy.keepColorProfile,
                      onCheckedChange = onKeepColorProfileChange)
            SwitchRow(title = R.string.keep_orientation,
                      description = R.string.keep_orientation_description,
                      checked = privacy.keepOrientation,
                      onCheckedChange = onKeepOrientationChange)
            SwitchRow(title = R.string.randomize_file_names,
                      description = R.string.randomize_file_names_description,
                      checked = privacy.randomizeFileNames,
                      onCheckedChange = onRandomizeFileNamesChange)

            SectionTitle(R.string.about)
            LinkRow(icon = R.drawable.star_24px, title = R.string.rate_and_review) {
                val packageName = context.packageName
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                                              .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
                }
            }
            LinkRow(icon = R.drawable.policy_24px, title = R.string.privacy_policy) {
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://pmahend1.github.io/metadata-wiper/privacy-policy.html".toUri()))
            }
            Row(modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painter = painterResource(id = R.drawable.info_24px),
                     contentDescription = null,
                     tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = stringResource(id = R.string.app_version),
                     style = MaterialTheme.typography.bodyLarge,
                     modifier = Modifier.weight(1f))
                Text(text = appVersion,
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(titleId: Int) {
    Text(text = stringResource(id = titleId),
         style = MaterialTheme.typography.titleSmall,
         color = MaterialTheme.colorScheme.primary,
         modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun SwitchRow(title: Int,
                      description: Int,
                      checked: Boolean,
                      onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(id = title), style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(id = description),
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The whole row toggles, so the switch itself takes no clicks.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int,
                    title: Int,
                    onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .heightIn(min = 56.dp)
        .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painter = painterResource(id = icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = stringResource(id = title), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun themeLabel(theme: Theme): Int {
    return when (theme) {
        Theme.System -> R.string.theme_system
        Theme.Light -> R.string.theme_light
        Theme.Dark -> R.string.theme_dark
    }
}
