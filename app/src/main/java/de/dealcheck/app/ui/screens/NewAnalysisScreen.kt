package de.dealcheck.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.dealcheck.app.DealCheckApp
import de.dealcheck.app.ui.NewAnalysisViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAnalysisScreen(viewModel: NewAnalysisViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as DealCheckApp

    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) viewModel.addImages(listOf(uri))
        pendingCameraUri = null
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(NewAnalysisViewModel.MAX_IMAGES),
    ) { uris -> viewModel.addImages(uris) }

    val busy = state.analyzing || state.importing

    Column {
        TopAppBar(title = { Text("Neue Analyse") })
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (settings.apiKey.isBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Noch kein API-Key eingetragen", style = MaterialTheme.typography.titleMedium)
                        Text("Für die Analyse brauchst du einen OpenRouter-API-Key.")
                        TextButton(onClick = onOpenSettings) { Text("Zu den Einstellungen") }
                    }
                }
            }

            Text(
                "Füge Fotos oder Screenshots eines eBay- oder Kleinanzeigen-Angebots hinzu " +
                    "(z. B. Anzeige und Beschreibung). Du kannst Bilder auch direkt aus anderen Apps " +
                    "über „Teilen“ → DealCheck schicken.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val file: File = app.images.newCameraFile()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri = uri
                        try {
                            camera.launch(uri)
                        } catch (_: Exception) {
                            pendingCameraUri = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Foto aufnehmen")
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Aus Galerie")
                }
            }

            if (state.imagePaths.isNotEmpty() || state.importing) {
                Text(
                    "Bilder (${state.imagePaths.size}/${NewAnalysisViewModel.MAX_IMAGES})",
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.imagePaths, key = { it }) { path ->
                        Box {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "Angebotsbild",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            if (!state.analyzing) {
                                FilledIconButton(
                                    onClick = { viewModel.removeImage(path) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) {
                                    Icon(Icons.Default.Close, "Bild entfernen", Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    if (state.importing) {
                        item {
                            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                enabled = !state.analyzing,
                label = { Text("Zusatzinfos (optional)") },
                placeholder = { Text("z. B. Preis, Zustand, was der Verkäufer am Telefon sagte") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            state.error?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Fehler", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = viewModel::dismissError) { Text("OK") }
                    }
                }
            }

            Button(
                onClick = viewModel::analyze,
                enabled = !busy && state.imagePaths.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (state.analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Analyse läuft …")
                } else {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Angebot analysieren")
                }
            }
            if (state.analyzing) {
                Text(
                    "Das kann je nach Modell" + (if (settings.webSearch) " und Websuche" else "") +
                        " bis zu einer Minute dauern.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Modell: ${settings.model}" + if (settings.webSearch) " · Websuche an" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
