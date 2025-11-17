package com.prateekmahendrakar.metadatawiper

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.prateekmahendrakar.metadatawiper.ui.theme.MetaDataWiperTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempFile

// Enum to represent theme options
enum class Theme {
    System, Light, Dark
}

// ViewModel to handle theme settings persistence
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // A private mutable state flow that can be updated from within the ViewModel
    private val _theme = MutableStateFlow(Theme.System)
    // The public, immutable state flow that the UI will observe
    val theme: StateFlow<Theme> = _theme

    init {
        // Read the saved theme from SharedPreferences when the ViewModel is created
        val savedThemeName = prefs.getString("theme", Theme.System.name) ?: Theme.System.name
        _theme.value = Theme.valueOf(savedThemeName)
    }

    fun setTheme(theme: Theme) {
        // Update the state flow, which will automatically notify the UI
        _theme.value = theme
        // Persist the new theme to SharedPreferences
        viewModelScope.launch {
            prefs.edit {
                putString("theme", theme.name)
            }
        }
    }
}


class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val theme by settingsViewModel.theme.collectAsState()
            val useDarkTheme = when (theme) {
                Theme.System -> isSystemInDarkTheme()
                Theme.Light -> false
                Theme.Dark -> true
            }

            MetaDataWiperTheme(darkTheme = useDarkTheme) {
                var metadataMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                var hasRemovableExifData by remember { mutableStateOf(false) }
                var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
                var cleanedFile by remember { mutableStateOf<File?>(null) }
                var showSettingsDialog by remember { mutableStateOf(false) }


                if (showSettingsDialog) {
                    SettingsDialog(
                        currentTheme = theme,
                        onThemeChange = { settingsViewModel.setTheme(it) },
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                    ) {
                        // --- Top Bar with Settings ---
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SettingsButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // --- Action Buttons ---
                            ActionButtons(
                                cleanedFile = cleanedFile,
                                hasRemovableExif = hasRemovableExifData,
                                isEnabled = selectedImageUri != null,
                                onImageSelected = { uri, metadata, file, hasRemovable ->
                                    selectedImageUri = uri
                                    cleanedFile = file
                                    metadataMap = metadata
                                    hasRemovableExifData = hasRemovable
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // --- Content Area ---
                            if (selectedImageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(selectedImageUri),
                                    contentDescription = "Selected Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MetadataTable(metadata = metadataMap)
                            } else {
                                Text(
                                    text = "Select an image to view its metadata and remove it.",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(8.dp)) {
        Icon(Icons.Default.Settings, contentDescription = "Settings")
    }
}

@Composable
fun SettingsDialog(
    currentTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    onDismiss: () -> Unit
) {
    var tempTheme by remember { mutableStateOf(currentTheme) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

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
                        Text(
                            text = theme.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onThemeChange(tempTheme)
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}


@Composable
fun ActionButtons(
    cleanedFile: File?,
    hasRemovableExif: Boolean,
    isEnabled: Boolean,
    onImageSelected: (Uri, HashMap<String, String>, File, Boolean) -> Unit
) {
    val context = LocalContext.current

    val openImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    val originalTempFile = createTempFile().toFile()
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        originalTempFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    val exifReader = ExifInterface(originalTempFile.absolutePath)
                    val allTags = getAllExifTags()
                    val displayHashMap = HashMap<String, String>()
                    for (tag in allTags) {
                        exifReader.getAttribute(tag)?.let { value -> displayHashMap[tag] = value }
                    }

                    val removableTags = getRemovableExifTags()
                    val hasRemovableData = removableTags.any { tag -> exifReader.getAttribute(tag) != null }

                    val cleanedTempFile = createTempFile().toFile()
                    originalTempFile.copyTo(cleanedTempFile, overwrite = true)

                    if (hasRemovableData) {
                        val exifWriter = ExifInterface(cleanedTempFile.absolutePath)
                        for (tag in removableTags) {
                            exifWriter.setAttribute(tag, null)
                        }
                        exifWriter.saveAttributes()
                    }

                    onImageSelected(it, displayHashMap, cleanedTempFile, hasRemovableData)

                } catch (e: IOException) {
                    Log.e("TAG", "Error processing file", e)
                    Toast.makeText(context, "Error processing file.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg"),
        onResult = { uri: Uri? ->
            uri?.let { saveUri ->
                cleanedFile?.let { file ->
                    try {
                        context.contentResolver.openOutputStream(saveUri).use { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream!!)
                            }
                        }
                        Toast.makeText(context, "Image saved successfully!", Toast.LENGTH_SHORT).show()
                    } catch (e: IOException) {
                        Log.e("TAG", "Failed to save file", e)
                        Toast.makeText(context, "Failed to save image.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { openImageLauncher.launch(arrayOf("image/*")) }) {
            Text(text = "Open Image")
        }
        Spacer(modifier = Modifier.padding(8.dp))
        if (isEnabled) {
            if (hasRemovableExif) {
                Button(onClick = { saveFileLauncher.launch("cleaned_image.jpg") }) {
                    Text(text = "Remove Exif data")
                }
            } else {
                Text("No removable EXIF data found.")
            }
        }
    }
}

@Composable
fun MetadataTable(metadata: Map<String, String>) {
    if (metadata.isEmpty()) {
        Text("No EXIF data found in the selected image.", modifier = Modifier.padding(16.dp))
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Tag", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Value", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            metadata.entries.forEach { (key, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(key, modifier = Modifier.weight(1f))
                    Text(value, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }
        }
    }
}

fun getEssentialExifTags(): Set<String> {
    return setOf(
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
        ExifInterface.TAG_SAMPLES_PER_PIXEL,
        ExifInterface.TAG_BITS_PER_SAMPLE,
        ExifInterface.TAG_COMPRESSION,
        ExifInterface.TAG_PLANAR_CONFIGURATION,
        ExifInterface.TAG_ROWS_PER_STRIP,
        ExifInterface.TAG_STRIP_BYTE_COUNTS,
        ExifInterface.TAG_STRIP_OFFSETS,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH
    )
}

fun getAllExifTags(): Array<String> {
    // This function remains to get ALL data for display purposes
    return ExifInterface::class.java.fields
        .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
        .map { it.get(null) as String }
        .toTypedArray()
}

fun getRemovableExifTags(): Array<String> {
    // This function gets all tags EXCEPT the essential ones
    val essentialTags = getEssentialExifTags()
    return getAllExifTags().filterNot { it in essentialTags }.toTypedArray()
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    MetaDataWiperTheme {
        val sampleMetadata = hashMapOf(
            "TAG_MAKE" to "Google",
            "TAG_MODEL" to "Pixel 8",
            "TAG_FOCAL_LENGTH" to "24mm"
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ActionButtons(
                cleanedFile = null,
                hasRemovableExif = true,
                isEnabled = true
            ) { _, _, _, _ -> }
            Spacer(modifier = Modifier.height(16.dp))
            MetadataTable(metadata = sampleMetadata)
        }
    }
}
