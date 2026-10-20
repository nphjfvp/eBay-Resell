package de.dealcheck.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.dealcheck.app.analysis.DealAnalysis
import de.dealcheck.app.analysis.ProfitCalc
import de.dealcheck.app.analysis.Recommendation
import de.dealcheck.app.analysis.SearchLinks
import de.dealcheck.app.ui.CalcFields
import de.dealcheck.app.ui.ResultViewModel
import de.dealcheck.app.ui.formatDate
import de.dealcheck.app.ui.formatEur
import de.dealcheck.app.ui.formatRange
import de.dealcheck.app.ui.theme.RecommendAvoid
import de.dealcheck.app.ui.theme.RecommendBuy
import de.dealcheck.app.ui.theme.RecommendNegotiate
import java.io.File
import java.util.Locale

fun Recommendation.color(): Color = when (this) {
    Recommendation.KAUFEN -> RecommendBuy
    Recommendation.VERHANDELN -> RecommendNegotiate
    Recommendation.NICHT_KAUFEN -> RecommendAvoid
    Recommendation.UNBEKANNT -> Color(0xFF757575)
}

fun Recommendation.onColor(): Color = if (this == Recommendation.VERHANDELN) Color.Black else Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(viewModel: ResultViewModel, onBack: () -> Unit) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val calc by viewModel.calc.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { Text("Ergebnis") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            },
            actions = {
                if (result?.isSuccess == true) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Analyse löschen") }
                }
            },
        )

        val r = result
        when {
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r.isFailure -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Diese Analyse ist nicht (mehr) vorhanden.")
            }
            else -> {
                val loaded = r.getOrThrow()
                LaunchedEffect(loaded.entity.id) { viewModel.initCalc(loaded) }
                ResultContent(
                    analysis = loaded.analysis,
                    imagePaths = loaded.entity.imagePaths,
                    createdAt = loaded.entity.createdAt,
                    model = loaded.entity.model,
                    calc = calc,
                    onCalcChange = viewModel::updateCalc,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Analyse löschen?") },
            text = { Text("Die Analyse und ihre Bilder werden vom Gerät entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ResultContent(
    analysis: DealAnalysis,
    imagePaths: List<String>,
    createdAt: Long,
    model: String,
    calc: CalcFields?,
    onCalcChange: (CalcFields) -> Unit,
) {
    val context = LocalContext.current
    val a = analysis

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Große, farbige Empfehlung
        Card(
            colors = CardDefaults.cardColors(containerColor = a.empfehlung.color(), contentColor = a.empfehlung.onColor()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(a.empfehlung.label, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(a.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BigFigure("Gewinnspanne", formatRange(a.gewinnMinEur, a.gewinnMaxEur))
                    BigFigure("Max. Kaufpreis", formatEur(a.maxKaufpreisEur))
                }
            }
        }

        if (a.warnsignaleBetrug.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "  Warnsignale für Betrug",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    BulletList(a.warnsignaleBetrug, MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        Section("Eckdaten") {
            KeyValue("Angebotspreis", formatEur(a.angebotspreisEur))
            KeyValue("Versand", formatEur(a.versandEur))
            KeyValue("Reparaturkosten", formatRange(a.reparaturKostenMinEur, a.reparaturKostenMaxEur))
            KeyValue("Verkaufspreis repariert", formatRange(a.verkaufspreisRepariertMinEur, a.verkaufspreisRepariertMaxEur))
            KeyValue("Gewinn", formatRange(a.gewinnMinEur, a.gewinnMaxEur))
            KeyValue("Schwierigkeit", a.schwierigkeit?.let { "$it / 5 " + "●".repeat(it) + "○".repeat(5 - it) } ?: "–")
        }

        a.begruendung?.let { Section("Begründung") { Text(it) } }

        if (a.risiken.isNotEmpty()) Section("Risiken") { BulletList(a.risiken) }

        Section("Zum Gerät") {
            a.defektLautAnzeige?.let { KeyValueBlock("Defekt laut Anzeige", it) }
            if (a.vermuteteUrsachen.isNotEmpty()) {
                Text("Vermutete Ursachen", style = MaterialTheme.typography.labelLarge)
                BulletList(a.vermuteteUrsachen)
            }
            a.verkaeuferHinweise?.let { KeyValueBlock("Hinweise zum Verkäufer", it) }
        }

        Text("Vergleichspreise: „${a.searchTerm}“", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { openUrl(context, SearchLinks.ebaySold(a.searchTerm)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ShoppingCart, null)
            Text("  Verkaufte auf eBay prüfen")
        }
        OutlinedButton(onClick = { openUrl(context, SearchLinks.kleinanzeigen(a.searchTerm)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Search, null)
            Text("  Auf Kleinanzeigen suchen")
        }

        if (calc != null) ProfitCalculator(calc, onCalcChange)

        if (imagePaths.isNotEmpty()) {
            Section("Bilder") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imagePaths) { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Angebotsbild",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }

        Text(
            "KI-Schätzung ohne Gewähr – Preise immer selbst prüfen.\n${formatDate(createdAt)} · Modell: $model",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfitCalculator(calc: CalcFields, onChange: (CalcFields) -> Unit) {
    val profit = ProfitCalc.compute(
        ProfitCalc.parseInput(calc.purchase),
        ProfitCalc.parseInput(calc.shipping),
        ProfitCalc.parseInput(calc.parts),
        ProfitCalc.parseInput(calc.sale),
    )
    Section("Eigene Gewinnrechnung") {
        Text(
            "Vorausgefüllt aus der Analyse (Mittelwerte), frei änderbar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Einkaufspreis", calc.purchase, Modifier.weight(1f)) { onChange(calc.copy(purchase = it)) }
            MoneyField("Versand", calc.shipping, Modifier.weight(1f)) { onChange(calc.copy(shipping = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Teile", calc.parts, Modifier.weight(1f)) { onChange(calc.copy(parts = it)) }
            MoneyField("Verkaufspreis", calc.sale, Modifier.weight(1f)) { onChange(calc.copy(sale = it)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (profit == null) {
            Text("Verkaufspreis eintragen, um den Gewinn zu berechnen.")
        } else {
            val color = if (profit.profit >= 0) RecommendBuy else RecommendAvoid
            KeyValue("Kosten gesamt", formatEur(profit.costs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Gewinn", style = MaterialTheme.typography.titleMedium)
                Text(formatEur(profit.profit), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            }
            KeyValue(
                "Marge (vom Verkaufspreis)",
                profit.marginPercent?.let { String.format(Locale.GERMANY, "%.1f %%", it) } ?: "–",
            )
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        suffix = { Text("€") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun BigFigure(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeyValueBlock(key: String, value: String) {
    Column {
        Text(key, style = MaterialTheme.typography.labelLarge)
        Text(value)
    }
}

@Composable
private fun BulletList(items: List<String>, color: Color = Color.Unspecified) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { Text("• $it", color = color) }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Kein Browser gefunden.", Toast.LENGTH_SHORT).show()
    }
}
