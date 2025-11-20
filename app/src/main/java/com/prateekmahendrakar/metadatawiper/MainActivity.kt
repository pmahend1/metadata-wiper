package com.prateekmahendrakar.metadatawiper

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    System,
    Light,
    Dark
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


@OptIn(ExperimentalMaterial3Api::class)
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
                var suggestedFileName by remember { mutableStateOf("cleaned_file.jpg") }


                if (showSettingsDialog) {
                    SettingsDialog(
                        currentTheme = theme,
                        onThemeChange = { settingsViewModel.setTheme(it) },
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Metadata Wiper",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            actions = {
                                SettingsButton(onClick = { showSettingsDialog = true })
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                ) { padding ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Action Buttons ---
                        ActionButtons(
                            cleanedFile = cleanedFile,
                            hasRemovableExif = hasRemovableExifData,
                            isEnabled = selectedImageUri != null,
                            suggestedFileName = suggestedFileName,
                            onImageSelected = { uri, metadata, file, hasRemovable, fileName ->
                                selectedImageUri = uri
                                cleanedFile = file
                                metadataMap = metadata
                                hasRemovableExifData = hasRemovable
                                suggestedFileName = fileName
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Content Area ---
                        if (selectedImageUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(selectedImageUri),
                                contentDescription = "Selected Image",
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 200.dp, minHeight = 200.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            MetadataTable(metadata = metadataMap)
                        } else {
                            Text(
                                text = "Select an image to view and remove its metadata.",
                                style = MaterialTheme.typography.bodyLarge,
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

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
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
    suggestedFileName: String,
    onImageSelected: (Uri, HashMap<String, String>, File, Boolean, String) -> Unit
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
                    val hasRemovableData =
                        removableTags.any { tag -> exifReader.getAttribute(tag) != null }

                    val cleanedTempFile = createTempFile().toFile()
                    originalTempFile.copyTo(cleanedTempFile, overwrite = true)

                    if (hasRemovableData) {
                        val exifWriter = ExifInterface(cleanedTempFile.absolutePath)
                        for (tag in removableTags) {
                            exifWriter.setAttribute(tag, null)
                        }
                        exifWriter.saveAttributes()
                    }

                    val originalFileName = getFileName(context, it) ?: "cleaned_file.jpg"
                    val newFileName = originalFileName.let {
                        val dotIndex = it.lastIndexOf('.')
                        if (dotIndex != -1) {
                            it.substring(0, dotIndex) + "_cleaned" + it.substring(dotIndex)
                        } else {
                            it + "_cleaned"
                        }
                    }

                    onImageSelected(
                        it,
                        displayHashMap,
                        cleanedTempFile,
                        hasRemovableData,
                        newFileName
                    )

                } catch (e: IOException) {
                    Log.e("TAG", "Error processing file", e)
                    Toast.makeText(context, "Error processing file.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*"),
        onResult = { uri: Uri? ->
            uri?.let { saveUri ->
                cleanedFile?.let { file ->
                    try {
                        context.contentResolver.openOutputStream(saveUri)?.let { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Toast.makeText(context, "Image saved successfully!", Toast.LENGTH_SHORT)
                            .show()
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
        FilledTonalButton(onClick = { openImageLauncher.launch(arrayOf("image/*")) }) {
            Text(text = "Open Image")
        }
        Spacer(modifier = Modifier.padding(8.dp))
        if (isEnabled) {
            if (hasRemovableExif) {
                Button(onClick = { saveFileLauncher.launch(suggestedFileName) }) {
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

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Tag", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Value(${metadata.size})", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            metadata.entries.forEach { (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(key, modifier = Modifier.weight(1f))
                    Text(value, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MetaDataWiperTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Metadata Wiper",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    actions = {
                        SettingsButton(onClick = { })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(
                    cleanedFile = null,
                    hasRemovableExif = false,
                    isEnabled = false,
                    suggestedFileName = "cleaned_file.jpg",
                    onImageSelected = { _, _, _, _, _ -> }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Select an image to view and remove its metadata.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
