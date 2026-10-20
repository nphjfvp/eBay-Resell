package de.dealcheck.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dealcheck.app.update.UpdateManager
import de.dealcheck.app.update.UpdateState

/** Hinweis auf eine neue Version, mit Download-Fortschritt und Installation. */
@Composable
fun UpdateDialog(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    val dismissed by updates.dismissed.collectAsStateWithLifecycle()
    val release = state.release ?: return
    if (release.version == dismissed) return

    AlertDialog(
        onDismissRequest = updates::dismiss,
        title = { Text("Update auf Version ${release.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Installiert: ${updates.currentVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (val s = state) {
                    is UpdateState.Downloading -> {
                        Text("Wird heruntergeladen … ${(s.progress * 100).toInt()} %")
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text("Download fertig. Tippe auf „Installieren“ und bestätige im nächsten Fenster.")
                        if (!updates.canInstall()) {
                            Text(
                                "Einmalig nötig: Erlaube DealCheck in den Einstellungen „Apps aus dieser Quelle installieren“, " +
                                    "kehre dann zurück und tippe erneut auf „Installieren“.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is UpdateState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    else -> {
                        Text("Eine neue Version von DealCheck ist verfügbar. Verlauf und Einstellungen bleiben erhalten.")
                        if (release.notes.isNotBlank()) {
                            Text(
                                release.notes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Downloading -> Unit
                is UpdateState.ReadyToInstall -> TextButton(onClick = { updates.install(s.file) }) { Text("Installieren") }
                is UpdateState.Error -> TextButton(onClick = { updates.download(release) }) { Text("Erneut versuchen") }
                else -> TextButton(onClick = { updates.download(release) }) { Text("Jetzt aktualisieren") }
            }
        },
        dismissButton = {
            TextButton(onClick = updates::dismiss) {
                Text(if (state is UpdateState.Downloading) "Im Hintergrund" else "Später")
            }
        },
    )
}
