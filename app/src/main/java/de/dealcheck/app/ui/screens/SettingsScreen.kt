package de.dealcheck.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dealcheck.app.data.AppSettings
import de.dealcheck.app.data.SettingsRepository
import de.dealcheck.app.ui.SettingsViewModel
import de.dealcheck.app.update.UpdateManager
import de.dealcheck.app.update.UpdateState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, updates: UpdateManager) {
    val saved by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var apiKey by rememberSaveable { mutableStateOf(saved.apiKey) }
    var model by rememberSaveable { mutableStateOf(saved.model) }
    var webSearch by rememberSaveable { mutableStateOf(saved.webSearch) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var savedHint by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(savedHint) {
        if (savedHint) {
            delay(2500)
            savedHint = false
        }
    }

    val changed = apiKey.trim() != saved.apiKey || model.trim() != saved.model || webSearch != saved.webSearch

    Column {
        TopAppBar(title = { Text("Einstellungen") })
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("OpenRouter-API-Key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gespeichert: ${SettingsRepository.mask(saved.apiKey)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("API-Key") },
                placeholder = { Text("sk-or-v1-…") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Verbergen" else "Anzeigen") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Der Key wird nur auf diesem Gerät gespeichert und ausschließlich an openrouter.ai gesendet.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/keys")))
                }
            }) { Text("Key auf openrouter.ai/keys erstellen") }

            HorizontalDivider()

            Text("Modell", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modellname (OpenRouter)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Das Modell muss Bilder verstehen (Vision). Standard: ${SettingsRepository.DEFAULT_MODEL}. " +
                    "Weitere Modelle findest du auf openrouter.ai/models.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (model.trim() != SettingsRepository.DEFAULT_MODEL) {
                TextButton(onClick = { model = SettingsRepository.DEFAULT_MODEL }) { Text("Standardmodell verwenden") }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Websuche", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Das Modell sucht im Web nach aktuellen Preisen. Genauer, aber langsamer und " +
                            "kostet bei OpenRouter etwas extra pro Anfrage.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = webSearch, onCheckedChange = { webSearch = it })
            }

            Button(
                onClick = {
                    viewModel.save(AppSettings(apiKey = apiKey, model = model, webSearch = webSearch))
                    model = model.trim().ifBlank { SettingsRepository.DEFAULT_MODEL }
                    savedHint = true
                },
                enabled = changed,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
            if (savedHint) Text("Gespeichert ✓", color = MaterialTheme.colorScheme.primary)

            HorizontalDivider()
            UpdateSection(updates)
        }
    }
}

@Composable
private fun UpdateSection(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    Text("App-Version", style = MaterialTheme.typography.titleMedium)
    Text("Installiert: ${updates.currentVersion}", style = MaterialTheme.typography.bodyMedium)
    when (val s = state) {
        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("  Suche nach Updates …")
        }
        UpdateState.UpToDate -> Text("Du hast die neueste Version.", color = MaterialTheme.colorScheme.primary)
        is UpdateState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
        is UpdateState.Downloading -> Text("Update ${s.release.version} wird geladen … ${(s.progress * 100).toInt()} %")
        else -> Unit
    }
    val available = state.release
    if (available != null && state !is UpdateState.Downloading) {
        Button(onClick = updates::show, modifier = Modifier.fillMaxWidth()) {
            Text("Update auf ${available.version} installieren")
        }
    } else {
        OutlinedButton(
            onClick = { updates.check() },
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Nach Updates suchen") }
    }
}
